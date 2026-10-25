package com.freshcommunity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.freshcommunity.dto.DemandCondition;
import com.freshcommunity.dto.MatchCandidate;
import com.freshcommunity.dto.SubscriptionCreateVO;
import com.freshcommunity.entity.GroupBuy;
import com.freshcommunity.entity.GroupBuyItem;
import com.freshcommunity.entity.Product;
import com.freshcommunity.entity.UserSubscription;
import com.freshcommunity.mapper.GroupBuyItemMapper;
import com.freshcommunity.mapper.GroupBuyMapper;
import com.freshcommunity.mapper.ProductMapper;
import com.freshcommunity.mapper.SubscriptionMatchMapper;
import com.freshcommunity.mapper.UserSubscriptionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 导购：需求订阅与匹配提醒。
 *
 * 三条匹配路径统一收敛到 {@link #tryMatchAndNotify}：
 *   1. 订阅创建时立即匹配进行中团购（{@link #matchAgainstOngoing}）
 *   2. 开团触发匹配全部订阅（{@link #matchNewGroupBuy}，由 GroupBuyService 调用）
 *   3. 定时补偿扫描（{@link #compensateMissed}），关闭并发"建订阅 × 开团"的漏提醒窗口
 *
 * 每个命中对先 INSERT IGNORE 抢占 subscription_match 记录（唯一键闸门），
 * 首占成功才发提醒——"同一订阅对同一团购只提醒一次"由数据库保证，
 * 与调用次数、路径与并发无关。提醒与记录同事务写入：回滚时一起消失，
 * 由补偿扫描重新补齐，不会出现"有记录没提醒"的死区。
 */
@Slf4j
@Service
public class SubscriptionService {

    private static final String PARSE_FAILURE = "未能识别需求中的商品，请换一种说法（例如：10 元以内的苹果）";

    private final UserSubscriptionMapper subscriptionMapper;
    private final GroupBuyMapper groupBuyMapper;
    private final GroupBuyItemMapper groupBuyItemMapper;
    private final ProductMapper productMapper;
    private final SubscriptionMatchMapper matchMapper;
    private final NotificationService notificationService;
    private final DemandParseService demandParseService;
    private final ReviewAnalysisService reviewAnalysisService;

    public SubscriptionService(UserSubscriptionMapper subscriptionMapper,
                               GroupBuyMapper groupBuyMapper,
                               GroupBuyItemMapper groupBuyItemMapper,
                               ProductMapper productMapper,
                               SubscriptionMatchMapper matchMapper,
                               NotificationService notificationService,
                               DemandParseService demandParseService,
                               ReviewAnalysisService reviewAnalysisService) {
        this.subscriptionMapper = subscriptionMapper;
        this.groupBuyMapper = groupBuyMapper;
        this.groupBuyItemMapper = groupBuyItemMapper;
        this.productMapper = productMapper;
        this.matchMapper = matchMapper;
        this.notificationService = notificationService;
        this.demandParseService = demandParseService;
        this.reviewAnalysisService = reviewAnalysisService;
    }

    /** 预览解析结果，不保存订阅。 */
    public DemandCondition preview(String text) {
        DemandCondition condition = demandParseService.parse(text);
        validateCondition(condition);
        return condition;
    }

    /** 创建订阅：确认条件或解析失败时拒绝创建；成功后立即匹配进行中的团购。 */
    @Transactional
    public SubscriptionCreateVO create(Long userId, String text) {
        return create(userId, text, null);
    }

    @Transactional
    public SubscriptionCreateVO create(Long userId, String text, DemandCondition confirmedCondition) {
        DemandCondition condition = confirmedCondition == null
                ? demandParseService.parse(text)
                : confirmedCondition;
        validateCondition(condition);

        UserSubscription sub = new UserSubscription();
        sub.setUserId(userId);
        sub.setKeyword(condition.getKeyword());
        sub.setMaxPrice(condition.getMaxPrice());
        sub.setSourceText(text.trim());
        subscriptionMapper.insert(sub);

        int matchedCount = matchAgainstOngoing(sub);

        SubscriptionCreateVO vo = new SubscriptionCreateVO();
        vo.setId(sub.getId());
        vo.setKeyword(sub.getKeyword());
        vo.setMaxPrice(sub.getMaxPrice());
        vo.setSourceText(sub.getSourceText());
        vo.setCreatedAt(sub.getCreatedAt());
        vo.setMatchedCount(matchedCount);
        return vo;
    }

    private void validateCondition(DemandCondition condition) {
        if (condition == null || condition.getKeyword() == null
                || condition.getKeyword().isBlank() || condition.getKeyword().length() > 64
                || (condition.getMaxPrice() != null && condition.getMaxPrice().signum() < 0)) {
            throw new IllegalArgumentException(PARSE_FAILURE);
        }
        condition.setKeyword(condition.getKeyword().trim());
    }

    /** 我的订阅列表（仅本人可见）。 */
    public List<UserSubscription> listByUser(Long userId) {
        return subscriptionMapper.selectList(new LambdaQueryWrapper<UserSubscription>()
                .eq(UserSubscription::getUserId, userId)
                .orderByDesc(UserSubscription::getCreatedAt));
    }

    /** 取消订阅：仅本人可取消（归属校验），逻辑删除。匹配记录保留——新订阅是新 id，不冲突。 */
    @Transactional
    public void cancel(Long userId, Long id) {
        UserSubscription sub = subscriptionMapper.selectById(id);
        if (sub == null || !sub.getUserId().equals(userId)) {
            throw new IllegalArgumentException("订阅不存在");
        }
        subscriptionMapper.deleteById(id);
    }

    /**
     * 开团触发：新团购创建后匹配全部有效订阅，命中即发送提醒。
     * 返回本次命中的订阅数量（供调用方观测，测试断言用）。
     */
    @Transactional
    public int matchNewGroupBuy(Long groupBuyId) {
        GroupBuy groupBuy = groupBuyMapper.selectById(groupBuyId);
        if (groupBuy == null) {
            return 0;
        }
        GroupBuyItem item = findItem(groupBuy.getId());
        Product product = item == null ? null : productMapper.selectById(item.getProductId());
        if (item == null || product == null) {
            return 0;
        }

        int matched = 0;
        List<UserSubscription> subs = subscriptionMapper.selectList(new LambdaQueryWrapper<>());
        for (UserSubscription sub : subs) {
            if (matches(sub, product.getName(), item.getPriceSnapshot())) {
                if (tryMatchAndNotify(sub.getId(), sub.getUserId(), groupBuy, product.getId(),
                        product.getName(), item.getPriceSnapshot())) {
                    matched++;
                }
            }
        }
        return matched;
    }

    /** 补偿扫描：找出符合条件但尚无匹配记录的「订阅 × 进行中团购」，补记录并发提醒。返回本次补偿条数。 */
    @Transactional
    public int compensateMissed() {
        List<MatchCandidate> missed = matchMapper.findMissedMatches();
        int count = 0;
        for (MatchCandidate c : missed) {
            // 候选行的进度字段是扫描时点的快照；提醒文案用最新团况，重新读一次团购
            GroupBuy groupBuy = groupBuyMapper.selectById(c.getGroupBuyId());
            if (groupBuy == null || !"ONGOING".equals(groupBuy.getStatus())) {
                continue;
            }
            if (tryMatchAndNotify(c.getSubscriptionId(), c.getUserId(), groupBuy,
                    c.getProductId(), c.getProductName(), c.getPriceSnapshot())) {
                count++;
            }
        }
        if (count > 0) {
            log.info("订阅匹配补偿扫描：本次补发 {} 条提醒", count);
        }
        return count;
    }

    /** 订阅时立即匹配：对当前进行中的团购逐个检查，命中即提醒。返回命中数量。 */
    private int matchAgainstOngoing(UserSubscription sub) {
        List<GroupBuy> ongoing = groupBuyMapper.selectList(new LambdaQueryWrapper<GroupBuy>()
                .eq(GroupBuy::getStatus, "ONGOING")
                .gt(GroupBuy::getDeadline, LocalDateTime.now()));
        int matched = 0;
        for (GroupBuy groupBuy : ongoing) {
            GroupBuyItem item = findItem(groupBuy.getId());
            Product product = item == null ? null : productMapper.selectById(item.getProductId());
            if (item != null && product != null && matches(sub, product.getName(), item.getPriceSnapshot())) {
                if (tryMatchAndNotify(sub.getId(), sub.getUserId(), groupBuy, product.getId(),
                        product.getName(), item.getPriceSnapshot())) {
                    matched++;
                }
            }
        }
        return matched;
    }

    /**
     * 统一匹配入口：INSERT IGNORE 抢占唯一记录，首占成功才发提醒。
     * 并发路径同时抢占同一对时，只有一条语句返回 1，提醒恰好发一次。
     */
    private boolean tryMatchAndNotify(Long subscriptionId, Long userId, GroupBuy groupBuy,
                                      Long productId, String productName, BigDecimal price) {
        int first = matchMapper.tryInsert(subscriptionId, groupBuy.getId());
        if (first == 0) {
            return false;
        }
        sendMatchNotify(userId, productName, price, groupBuy, productId);
        return true;
    }

    /** 匹配规则：商品名包含关键词（子串），且拼团价不超过价格上限（上限为空则只看关键词）。 */
    private boolean matches(UserSubscription sub, String productName, BigDecimal price) {
        if (sub.getKeyword() == null || productName == null || !productName.contains(sub.getKeyword())) {
            return false;
        }
        return sub.getMaxPrice() == null || price.compareTo(sub.getMaxPrice()) <= 0;
    }

    private void sendMatchNotify(Long userId, String productName, BigDecimal price, GroupBuy groupBuy, Long productId) {
        int remain = Math.max(groupBuy.getTargetCount() - groupBuy.getMemberCount(), 0);
        String content = String.format("「%s」正在团购：拼团价 ¥%s，还差 %d 人成团，去看看吧。",
                productName, price.toPlainString(), remain);

        List<String> tags = reviewAnalysisService.topTags(productId, 3);
        if (!tags.isEmpty()) {
            long analyzed = reviewAnalysisService.analyzedCount(productId);
            content += String.format("已有 %d 人评价都说%s。", analyzed, String.join("、", tags));
        }

        notificationService.notifyGroupEvent(userId, NotificationService.TYPE_SUBSCRIPTION_MATCH,
                "你关注的商品开团了", content, groupBuy.getId());
    }

    private GroupBuyItem findItem(Long groupBuyId) {
        return groupBuyItemMapper.selectOne(new LambdaQueryWrapper<GroupBuyItem>()
                .eq(GroupBuyItem::getGroupBuyId, groupBuyId)
                .last("LIMIT 1"));
    }
}

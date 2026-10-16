package com.freshcommunity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.freshcommunity.dto.DemandCondition;
import com.freshcommunity.dto.SubscriptionCreateVO;
import com.freshcommunity.entity.GroupBuy;
import com.freshcommunity.entity.GroupBuyItem;
import com.freshcommunity.entity.Product;
import com.freshcommunity.entity.UserSubscription;
import com.freshcommunity.mapper.GroupBuyItemMapper;
import com.freshcommunity.mapper.GroupBuyMapper;
import com.freshcommunity.mapper.ProductMapper;
import com.freshcommunity.mapper.UserSubscriptionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 导购：需求订阅与匹配提醒。
 *
 * - 订阅创建（{@link #create}）：解析自然语言 → 存储条件 → 立即匹配进行中团购（命中即提醒）；
 * - 开团触发（{@link #matchNewGroupBuy}）：新团购创建后由 GroupBuyService 调用，
 *   匹配全部有效订阅，命中即向订阅用户发送站内信。
 *
 * 通知复用 NotificationService（类型 SUBSCRIPTION_MATCH），与业务同事务写入，
 * 保证"订阅/开团"与"提醒"原子。匹配对象为商品名关键词 + 拼团价（price_snapshot）。
 */
@Service
public class SubscriptionService {

    private static final String PARSE_FAILURE = "未能识别需求中的商品，请换一种说法（例如：10 元以内的苹果）";

    private final UserSubscriptionMapper subscriptionMapper;
    private final GroupBuyMapper groupBuyMapper;
    private final GroupBuyItemMapper groupBuyItemMapper;
    private final ProductMapper productMapper;
    private final NotificationService notificationService;
    private final DemandParseService demandParseService;

    public SubscriptionService(UserSubscriptionMapper subscriptionMapper,
                               GroupBuyMapper groupBuyMapper,
                               GroupBuyItemMapper groupBuyItemMapper,
                               ProductMapper productMapper,
                               NotificationService notificationService,
                               DemandParseService demandParseService) {
        this.subscriptionMapper = subscriptionMapper;
        this.groupBuyMapper = groupBuyMapper;
        this.groupBuyItemMapper = groupBuyItemMapper;
        this.productMapper = productMapper;
        this.notificationService = notificationService;
        this.demandParseService = demandParseService;
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

    /** 取消订阅：仅本人可取消（归属校验），逻辑删除。 */
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
                sendMatchNotify(sub.getUserId(), product.getName(), item.getPriceSnapshot(), groupBuy);
                matched++;
            }
        }
        return matched;
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
                sendMatchNotify(sub.getUserId(), product.getName(), item.getPriceSnapshot(), groupBuy);
                matched++;
            }
        }
        return matched;
    }

    /** 匹配规则：商品名包含关键词（子串），且拼团价不超过价格上限（上限为空则只看关键词）。 */
    private boolean matches(UserSubscription sub, String productName, BigDecimal price) {
        if (sub.getKeyword() == null || productName == null || !productName.contains(sub.getKeyword())) {
            return false;
        }
        return sub.getMaxPrice() == null || price.compareTo(sub.getMaxPrice()) <= 0;
    }

    private void sendMatchNotify(Long userId, String productName, BigDecimal price, GroupBuy groupBuy) {
        int remain = Math.max(groupBuy.getTargetCount() - groupBuy.getMemberCount(), 0);
        notificationService.notify(userId, NotificationService.TYPE_SUBSCRIPTION_MATCH,
                "你关注的商品开团了",
                String.format("「%s」正在团购：拼团价 ¥%s，还差 %d 人成团，去看看吧。",
                        productName, price.toPlainString(), remain),
                null);
    }

    private GroupBuyItem findItem(Long groupBuyId) {
        return groupBuyItemMapper.selectOne(new LambdaQueryWrapper<GroupBuyItem>()
                .eq(GroupBuyItem::getGroupBuyId, groupBuyId)
                .last("LIMIT 1"));
    }
}

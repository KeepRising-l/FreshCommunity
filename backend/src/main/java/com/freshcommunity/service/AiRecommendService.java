package com.freshcommunity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.freshcommunity.dto.AiRecommendVO;
import com.freshcommunity.entity.GroupBuy;
import com.freshcommunity.entity.GroupBuyItem;
import com.freshcommunity.entity.Product;
import com.freshcommunity.mapper.GroupBuyItemMapper;
import com.freshcommunity.mapper.ProductMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 即时推荐：把在售商品、进行中团购与口碑标签组装成候选清单交给 LLM，
 * 生成基于真实数据的推荐回答，并从回答中回扫被点名的商品渲染为卡片。
 *
 * 原则：LLM 只引用候选清单里的信息（提示词约束 + 名称回扫锚定），
 * LLM 不可用时明确报错（"AI 服务暂时不可用"），绝不伪造推荐。
 */
@Slf4j
@Service
public class AiRecommendService {

    private static final int MAX_ITEMS = 6;

    private final ProductMapper productMapper;
    private final GroupBuyItemMapper groupBuyItemMapper;
    private final GroupBuyService groupBuyService;
    private final ReviewAnalysisService reviewAnalysisService;
    private final AiClient aiClient;
    private final com.freshcommunity.mapper.UserMapper userMapper;

    public AiRecommendService(ProductMapper productMapper,
                              GroupBuyItemMapper groupBuyItemMapper,
                              GroupBuyService groupBuyService,
                              ReviewAnalysisService reviewAnalysisService,
                              AiClient aiClient,
                              com.freshcommunity.mapper.UserMapper userMapper) {
        this.productMapper = productMapper;
        this.groupBuyItemMapper = groupBuyItemMapper;
        this.groupBuyService = groupBuyService;
        this.reviewAnalysisService = reviewAnalysisService;
        this.aiClient = aiClient;
        this.userMapper = userMapper;
    }

    public AiRecommendVO recommend(String text) {
        if (!aiClient.isAvailable()) {
            throw new IllegalArgumentException("AI 服务暂时不可用，请稍后重试");
        }

        List<Product> products = productMapper.selectList(new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, 1)
                .orderByAsc(Product::getId));
        if (products.isEmpty()) {
            throw new IllegalArgumentException("平台暂无在售商品，无法推荐");
        }
        List<GroupBuy> ongoing = groupBuyService.listOngoing();
        Map<Long, GroupBuy> ongoingByProductId = ongoingByProductId(ongoing);
        Map<Long, List<String>> tagsByProduct = reviewAnalysisService
                .topTags(products.stream().map(Product::getId).toList(), 3);
        Map<Long, com.freshcommunity.entity.User> leadersByGroupId = leadersOf(ongoing);

        String candidateText = buildCandidates(products, ongoingByProductId, tagsByProduct);
        String reply = aiClient.chat(SYSTEM_PROMPT, "用户需求：" + text + "\n\n候选商品清单：\n" + candidateText, false);
        if (reply == null || reply.isBlank()) {
            log.warn("AI 推荐调用失败：LLM 返回空");
            throw new IllegalArgumentException("AI 服务暂时没有响应，请稍后重试");
        }

        AiRecommendVO vo = new AiRecommendVO();
        vo.setReply(reply.trim());
        vo.setItems(pickMentionedItems(reply, products, ongoingByProductId, tagsByProduct, leadersByGroupId));
        return vo;
    }

    private static final String SYSTEM_PROMPT = """
            你是社区生鲜团购平台的 AI 导购助手。用户用一句话描述需求，你从候选商品清单中挑选并推荐。
            规则：
            1. 只使用清单里的信息，绝不编造清单外的商品、价格或口碑；
            2. 结合拼团价（低于零售价更划算）、用户预算、库存与口碑标签综合权衡，性价比优先；
            3. 回答分三段，用换行分隔：第一行以"推荐"开头（商品名 + 拼团价 + 一句话理由）；
               第二行"也可以看看："跟 1-2 个备选；最后一行"小贴士："给一句实用建议；
            4. 没有合适商品时，直接说明原因并从清单里给出最接近的替代建议；
            5. 全程中文，总共不超过 150 字，不要使用 markdown 符号。""";

    /** 候选清单：一行一个商品，含在团信息与口碑（LLM 引用的唯一信息来源）。 */
    private String buildCandidates(List<Product> products,
                                   Map<Long, GroupBuy> ongoingByProductId,
                                   Map<Long, List<String>> tagsByProduct) {
        StringBuilder sb = new StringBuilder();
        LocalDateTime now = LocalDateTime.now();
        for (Product p : products) {
            sb.append('#').append(p.getId()).append(' ').append(p.getName())
                    .append('[').append(p.getCategory()).append(']')
                    .append(" 零售").append(p.getPrice()).append("元 库存").append(p.getStock());
            GroupBuy gb = ongoingByProductId.get(p.getId());
            if (gb != null) {
                long hours = Duration.between(now, gb.getDeadline()).toHours();
                sb.append(" ｜在团:「").append(gb.getTitle()).append("」拼团价")
                        .append(gb.getItemPrice()).append("元 已参团")
                        .append(gb.getMemberCount()).append('/').append(gb.getTargetCount())
                        .append("人 剩").append(Math.max(hours, 0)).append("小时");
            }
            List<String> tags = tagsByProduct.get(p.getId());
            if (tags != null && !tags.isEmpty()) {
                sb.append(" ｜口碑:").append(String.join(",", tags));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /** 该商品若有多个进行中团购，取参团进度最高的一个（演示口径，一团一商品）。 */
    private Map<Long, GroupBuy> ongoingByProductId(List<GroupBuy> ongoing) {
        Map<Long, GroupBuy> result = new LinkedHashMap<>();
        for (GroupBuy gb : ongoing) {
            List<GroupBuyItem> items = groupBuyItemMapper.selectList(
                    new LambdaQueryWrapper<GroupBuyItem>().eq(GroupBuyItem::getGroupBuyId, gb.getId()));
            if (items.isEmpty()) {
                continue;
            }
            gb.setItemPrice(items.get(0).getPriceSnapshot());
            result.merge(items.get(0).getProductId(), gb, (a, b) -> {
                double pa = a.getMemberCount() / Math.max(a.getTargetCount(), 1.0);
                double pb = b.getMemberCount() / Math.max(b.getTargetCount(), 1.0);
                return pb > pa ? b : a;
            });
        }
        return result;
    }

    /** 进行中团购的团长资料（提货点展示用），一次批量查询。 */
    private Map<Long, com.freshcommunity.entity.User> leadersOf(List<GroupBuy> ongoing) {
        List<Long> leaderIds = ongoing.stream().map(GroupBuy::getLeaderId).distinct().toList();
        if (leaderIds.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(leaderIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        com.freshcommunity.entity.User::getId, u -> u));
    }

    /**
     * 名称回扫：找出回答中被点名的商品，按首次出现位置排序（LLM 的首选排最前）。
     * 长名优先只用于匹配去歧义（防将来出现"苹果"短名商品在"红富士苹果"位置被误命中），
     * 排序以位置为准；同一商品只收一次，最多 6 个。
     */
    private List<AiRecommendVO.ItemVO> pickMentionedItems(String reply, List<Product> products,
                                                          Map<Long, GroupBuy> ongoingByProductId,
                                                          Map<Long, List<String>> tagsByProduct,
                                                          Map<Long, com.freshcommunity.entity.User> leadersByGroupId) {
        List<Product> sorted = new ArrayList<>(products);
        sorted.sort((a, b) -> Integer.compare(b.getName().length(), a.getName().length()));

        Map<Integer, AiRecommendVO.ItemVO> byPosition = new java.util.TreeMap<>();
        for (Product p : sorted) {
            int pos = reply.indexOf(p.getName());
            if (pos < 0) {
                continue;
            }
            AiRecommendVO.ItemVO item = new AiRecommendVO.ItemVO();
            item.setProductId(p.getId());
            item.setName(p.getName());
            item.setCategory(p.getCategory());
            item.setPrice(p.getPrice());
            item.setOriginalPrice(p.getPrice());
            item.setImageUrl(p.getImageUrl());
            List<String> tags = tagsByProduct.get(p.getId());
            item.setInsightTags(tags == null ? List.of() : tags);

            GroupBuy gb = ongoingByProductId.get(p.getId());
            if (gb != null) {
                item.setGroupBuyId(gb.getId());
                item.setGroupTitle(gb.getTitle());
                item.setMemberCount(gb.getMemberCount());
                item.setTargetCount(gb.getTargetCount());
                item.setPrice(gb.getItemPrice());
                com.freshcommunity.entity.User leader = leadersByGroupId.get(gb.getLeaderId());
                if (leader != null) {
                    item.setPickupPointName(leader.getPickupPointName());
                }
            }
            byPosition.put(pos, item);
            if (byPosition.size() >= MAX_ITEMS) {
                break;
            }
        }
        return new ArrayList<>(byPosition.values());
    }
}

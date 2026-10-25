package com.freshcommunity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.freshcommunity.entity.Review;
import com.freshcommunity.entity.ReviewAnalysis;
import com.freshcommunity.mapper.ReviewAnalysisMapper;
import com.freshcommunity.mapper.ReviewMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 评价洞察：把评价文本分析为情感倾向 + 关键词标签，写入 review_analysis（每评一行）。
 *
 * 分析策略与需求解析一致——LLM 优先，超时/异常/结果不合法时回落规则词典，
 * 保证无 key 环境仍能生成洞察数据用于演示。
 * 展示聚合（词频 top N）在内存完成：数据量小，不做 SQL 复杂聚合。
 */
@Service
public class ReviewAnalysisService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** 正面词典 → 标签。键按「长的在前」排列：先命中更具体的短语，避免单字误伤。 */
    private static final Map<String, String> POSITIVE_WORDS = orderedMap(
            "很新鲜", "新鲜", "新鲜", "新鲜", "好吃", "好吃",
            "性价比", "实惠", "实惠", "实惠", "划算", "划算", "便宜", "实惠",
            "满意", "满意", "到货快", "到货快", "到货很快", "到货快",
            "很甜", "很甜", "甜", "甜");
    /** 负面词典 → 标签 */
    private static final Map<String, String> NEGATIVE_WORDS = orderedMap(
            "不新鲜", "不新鲜", "货不对板", "货不对板", "有异味", "有异味",
            "分量少", "分量少", "到货慢", "到货慢", "体验差", "体验差",
            "烂", "不新鲜", "贵", "偏贵", "慢", "到货慢", "差", "体验差",
            "忽悠", "货不对板", "异味", "有异味", "缩水", "分量少");

    /** 否定前缀（长的在前）：紧邻这些前缀的正向词不算肯定表达（如「不新鲜」「没有新鲜感」）。 */
    private static final String[] NEGATION_PREFIXES = {"不怎么", "不太", "不够", "没有", "不", "没", "难"};

    private final ReviewMapper reviewMapper;
    private final ReviewAnalysisMapper analysisMapper;
    private final AiClient aiClient;

    public ReviewAnalysisService(ReviewMapper reviewMapper, ReviewAnalysisMapper analysisMapper, AiClient aiClient) {
        this.reviewMapper = reviewMapper;
        this.analysisMapper = analysisMapper;
        this.aiClient = aiClient;
    }

    /** 分析单条评价并落库（已存在则跳过，保证 1:1）。 */
    @Transactional
    public void analyze(Long reviewId) {
        Review review = reviewMapper.selectById(reviewId);
        if (review == null || review.getContent() == null || review.getContent().isBlank()) {
            return;
        }
        Long exists = analysisMapper.selectCount(
                new LambdaQueryWrapper<ReviewAnalysis>().eq(ReviewAnalysis::getReviewId, reviewId));
        if (exists != null && exists > 0) {
            return;
        }

        Result result = aiClient.isAvailable() ? analyzeByLlm(review.getContent()) : null;
        if (result == null) {
            result = analyzeByRule(review.getContent());
        }

        ReviewAnalysis analysis = new ReviewAnalysis();
        analysis.setReviewId(reviewId);
        analysis.setProductId(review.getProductId());
        analysis.setSentiment(result.sentiment());
        analysis.setTags(String.join(",", result.tags()));
        analysisMapper.insert(analysis);
    }

    /** 批量分析"有文字且无分析记录"的评价。返回本次分析条数。 */
    @Transactional
    public int analyzePending() {
        List<Long> ids = analysisMapper.findUnanalyzedReviewIds();
        int count = 0;
        for (Long id : ids) {
            analyze(id);
            count++;
        }
        return count;
    }

    /** 该商品高频口碑标签 top N（无数据返回空列表）。 */
    public List<String> topTags(Long productId, int limit) {
        return topTags(List.of(productId), limit).getOrDefault(productId, List.of());
    }

    /**
     * 批量版口碑标签：一次 IN 查询取多个商品的标签串，内存分组聚合。
     * 团购列表页对每个商品逐个调 topTags 会形成 N+1 查询，列表场景一律走这里。
     */
    public Map<Long, List<String>> topTags(List<Long> productIds, int limit) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<String>> tagStringsByProduct = new HashMap<>();
        for (Map<String, Object> row : analysisMapper.findTagRowsByProducts(productIds)) {
            Long pid = ((Number) row.get("productId")).longValue();
            tagStringsByProduct.computeIfAbsent(pid, k -> new ArrayList<>()).add((String) row.get("tags"));
        }
        Map<Long, List<String>> result = new LinkedHashMap<>();
        for (Long productId : productIds) {
            List<String> tagStrings = tagStringsByProduct.getOrDefault(productId, List.of());
            if (tagStrings.isEmpty()) {
                result.put(productId, List.of());
                continue;
            }
            Map<String, Long> freq = tagStrings.stream()
                    .flatMap(s -> List.of(s.split(",")).stream())
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.groupingBy(s -> s, Collectors.counting()));
            result.put(productId, freq.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(limit)
                    .map(Map.Entry::getKey)
                    .toList());
        }
        return result;
    }

    /** 该商品被分析的评价数（供提醒摘要"已有 N 人评价"）。 */
    public long analyzedCount(Long productId) {
        return analysisMapper.countByProduct(productId);
    }

    // ---------- 分析实现 ----------

    private record Result(String sentiment, List<String> tags) {}

    private Result analyzeByLlm(String content) {
        String system = "你是生鲜团购平台的口碑分析器。分析用户评价，输出 JSON："
                + "{\"sentiment\": \"POSITIVE|NEGATIVE|NEUTRAL\", \"tags\": [\"标签1\",\"标签2\"]}。"
                + "tags 是 1~3 个简短中文口碑词（如 新鲜、实惠、到货快、偏贵），只输出 JSON。";
        String out = aiClient.chat(system, "评价内容：" + content, true);
        if (out == null) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(extractJson(out));
            String sentiment = node.path("sentiment").asText("NEUTRAL");
            if (!List.of("POSITIVE", "NEGATIVE", "NEUTRAL").contains(sentiment)) {
                sentiment = "NEUTRAL";
            }
            List<String> tags = new ArrayList<>();
            for (JsonNode t : node.path("tags")) {
                String tag = t.asText("").trim();
                if (!tag.isEmpty() && tag.length() <= 16) {
                    tags.add(tag);
                }
                if (tags.size() >= 3) {
                    break;
                }
            }
            return tags.isEmpty() ? null : new Result(sentiment, tags);
        } catch (Exception e) {
            return null;
        }
    }

    private Result analyzeByRule(String content) {
        List<String> tags = new ArrayList<>();
        boolean negative = false;
        boolean positive = false;
        for (Map.Entry<String, String> e : POSITIVE_WORDS.entrySet()) {
            if (containsAffirmed(content, e.getKey()) && !tags.contains(e.getValue())) {
                tags.add(e.getValue());
                positive = true;
            }
        }
        for (Map.Entry<String, String> e : NEGATIVE_WORDS.entrySet()) {
            if (content.contains(e.getKey()) && !tags.contains(e.getValue())) {
                tags.add(e.getValue());
                negative = true;
            }
        }
        if (tags.size() > 3) {
            tags = new ArrayList<>(tags.subList(0, 3));
        }
        String sentiment = negative && !positive ? "NEGATIVE"
                : positive && !negative ? "POSITIVE" : "NEUTRAL";
        // 情感倾向与关键词标签是两回事：中性评价不产出标签，
        // 否则「中性」会混进口碑标签区与提醒摘要（"都说中性、新鲜"）。
        return new Result(sentiment, tags);
    }

    /**
     * 正向词是否被「肯定」地提及：「不新鲜」「没有新鲜感」里的「新鲜」是负面表达，
     * 不能因为子串命中就记成正面标签。只看紧邻的前缀，够用且不会误伤「很新鲜」。
     */
    private boolean containsAffirmed(String content, String word) {
        int from = 0;
        while (true) {
            int idx = content.indexOf(word, from);
            if (idx < 0) {
                return false;
            }
            if (!isNegated(content, idx)) {
                return true;
            }
            from = idx + word.length();
        }
    }

    /** 判断位置 idx 前的紧邻前缀是否为否定表达（「不」「没」「没有」「不太」等）。 */
    private boolean isNegated(String content, int idx) {
        for (String negation : NEGATION_PREFIXES) {
            int start = idx - negation.length();
            if (start >= 0 && content.startsWith(negation, start)) {
                return true;
            }
        }
        return false;
    }

    private String extractJson(String content) {
        String t = content.trim();
        int start = t.indexOf('{');
        int end = t.lastIndexOf('}');
        return (start >= 0 && end > start) ? t.substring(start, end + 1) : t;
    }

    private static Map<String, String> orderedMap(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }
}

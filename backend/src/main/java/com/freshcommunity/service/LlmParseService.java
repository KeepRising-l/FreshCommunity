package com.freshcommunity.service;

import com.freshcommunity.dto.DemandCondition;
import com.freshcommunity.mapper.ProductMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 需求解析的组合实现：配置了 LLM key 则优先调用 {@link AiClient}，
 * 超时 / 异常 / 结果不合法（keyword 为空、价格非法）时回落规则解析 {@link RuleDemandParseService}；
 * 两条链路均失败返回 {@code null}（调用方提示用户换一种说法）。
 *
 * 无 key 环境下直接走规则，保证应用正常启动。
 * 标记 {@link Primary}，让订阅服务注入本组合解析器而非纯规则实现。
 */
@Service
@Primary
public class LlmParseService implements DemandParseService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AiClient aiClient;
    private final RuleDemandParseService ruleFallback;
    private final ProductMapper productMapper;

    public LlmParseService(AiClient aiClient, RuleDemandParseService ruleFallback, ProductMapper productMapper) {
        this.aiClient = aiClient;
        this.ruleFallback = ruleFallback;
        this.productMapper = productMapper;
    }

    @Override
    public DemandCondition parse(String text) {
        if (!aiClient.isAvailable()) {
            return ruleFallback.parse(text);
        }
        DemandCondition llm = parseByLlm(text);
        return isValid(llm) ? llm : ruleFallback.parse(text);
    }

    /** 调用 LLM 解析；任何异常（连接、超时、响应解析）均返回 null 触发回落。 */
    private DemandCondition parseByLlm(String text) {
        String system = "你是生鲜团购平台的商品需求解析器。把用户的一句话需求解析为 {\"keyword\", \"maxPrice\"} 两个字段的 JSON。"
                + "keyword 是从候选商品中挑最匹配的名字做归一（找不到时用口语词本身），"
                + "maxPrice 是用户期望的价格上限（数字，没提价格时为 null）。只输出 JSON。";
        String user = "候选商品：" + candidateNames() + "\n用户需求：" + text;

        String content = aiClient.chat(system, user, true);
        return content == null ? null : parseContent(content);
    }

    private DemandCondition parseContent(String content) {
        try {
            var node = MAPPER.readTree(extractJson(content));
            String keyword = node.path("keyword").asText(null);
            BigDecimal maxPrice = node.path("maxPrice").isNull() ? null : node.path("maxPrice").decimalValue();
            if (keyword == null || keyword.isBlank() || keyword.length() > 64) {
                return null;
            }
            if (maxPrice != null && maxPrice.signum() < 0) {
                maxPrice = null;
            }
            return new DemandCondition(keyword, maxPrice);
        } catch (Exception e) {
            return null;
        }
    }

    /** LLM 可能返回 ```json ... ``` 包裹，提取首个 JSON 对象。 */
    private String extractJson(String content) {
        String t = content.trim();
        int start = t.indexOf('{');
        int end = t.lastIndexOf('}');
        return (start >= 0 && end > start) ? t.substring(start, end + 1) : t;
    }

    private String candidateNames() {
        List<String> names = productMapper.selectList(null).stream().map(p -> p.getName()).toList();
        return names.toString();
    }

    private boolean isValid(DemandCondition c) {
        return c != null && c.getKeyword() != null && !c.getKeyword().isBlank()
                && c.getKeyword().length() <= 64;
    }
}

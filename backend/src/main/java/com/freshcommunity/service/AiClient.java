package com.freshcommunity.service;

import com.freshcommunity.config.AiProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容接口的轻量客户端：封装 chat/completions 调用。
 * 无 key 时 {@link #isAvailable()} 为 false，调用方应回落规则实现。
 * 任何调用异常（连接、超时、响应格式）统一返回 null，由调用方决定降级策略。
 */
@Component
public class AiClient {

    private final AiProperties aiProperties;
    private final RestClient restClient;

    public AiClient(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
        if (!aiProperties.isAvailable()) {
            this.restClient = null;
            return;
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) aiProperties.getTimeoutSeconds() * 1000);
        factory.setReadTimeout((int) aiProperties.getTimeoutSeconds() * 1000);
        this.restClient = RestClient.builder()
                .baseUrl(aiProperties.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + aiProperties.getApiKey())
                .requestFactory(factory)
                .build();
    }

    /** 是否已配置 key、可以调用 LLM。 */
    public boolean isAvailable() {
        return restClient != null;
    }

    /**
     * 单轮对话，返回模型输出文本；无 key 或调用失败返回 null。
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @param jsonMode     是否要求返回 JSON 对象（response_format=json_object）
     */
    @SuppressWarnings("unchecked")
    public String chat(String systemPrompt, String userPrompt, boolean jsonMode) {
        if (restClient == null) {
            return null;
        }
        try {
            List<Map<String, String>> messages = List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userPrompt));
            java.util.HashMap<String, Object> body = new java.util.HashMap<>();
            body.put("model", aiProperties.getModel());
            body.put("temperature", 0);
            body.put("messages", messages);
            if (jsonMode) {
                body.put("response_format", Map.of("type", "json_object"));
            }
            if (!aiProperties.isThinkingEnabled()) {
                // DeepSeek 思考模式标准关闭参数（思考内容在独立 reasoning_content 字段，
                // 本方法只取 content，最终回答不受影响）
                body.put("chat_template_kwargs", Map.of("thinking", false));
            }

            Map<String, Object> resp = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            if (resp == null) {
                return null;
            }
            Object choices = resp.get("choices");
            if (!(choices instanceof List<?> list) || list.isEmpty()) {
                return null;
            }
            Object message = ((Map<String, Object>) list.get(0)).get("message");
            Object content = message == null ? null : ((Map<String, Object>) message).get("content");
            return content == null ? null : String.valueOf(content);
        } catch (Exception e) {
            return null;
        }
    }
}

package com.freshcommunity.service;

import com.freshcommunity.config.AiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * OpenAI 兼容接口的轻量客户端：封装 chat/completions 调用（同步 + 流式）。
 * 无 key 时 {@link #isAvailable()} 为 false，调用方应回落规则实现。
 * 任何调用异常（连接、超时、响应格式）统一返回 null，由调用方决定降级策略。
 */
@Component
public class AiClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

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

    /** 单轮对话，返回模型输出文本；无 key 或调用失败返回 null。 */
    public String chat(String systemPrompt, String userPrompt, boolean jsonMode) {
        return chat(messagesOf(systemPrompt, userPrompt), jsonMode);
    }

    /** 多轮对话（history 为已发生的问答对，不含 system）。 */
    public String chat(String systemPrompt, List<Map<String, String>> history,
                       String userPrompt, boolean jsonMode) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.addAll(history);
        messages.add(Map.of("role", "user", "content", userPrompt));
        return chat(messages, jsonMode);
    }

    /**
     * 流式对话：逐段回调 onDelta（仅正文增量），全部完成后返回完整正文（失败返回 null）。
     * 网关的思考内容在 delta.reasoning_content，只有 delta.content 进入回调与结果。
     */
    public String chatStream(String systemPrompt, List<Map<String, String>> history,
                             String userPrompt, Consumer<String> onDelta) {
        if (restClient == null) {
            return null;
        }
        try {
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemPrompt));
            if (history != null) {
                messages.addAll(history);
            }
            messages.add(Map.of("role", "user", "content", userPrompt));

            StringBuilder full = new StringBuilder();
            restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(bodyOf(messages, false, true))
                    .exchange((req, resp) -> {
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(resp.getBody(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                String delta = parseStreamDelta(line);
                                if (delta == null) {
                                    continue; // [DONE] / 注释行 / 思考内容
                                }
                                full.append(delta);
                                onDelta.accept(delta);
                            }
                        }
                        return true;
                    });
            return full.isEmpty() ? null : full.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** 从 SSE 行解析正文增量：仅取 choices[0].delta.content；[DONE]/思考行返回 null。 */
    public static String parseStreamDelta(String line) {
        if (line == null || !line.startsWith("data:")) {
            return null;
        }
        String payload = line.substring(5).trim();
        if (payload.isEmpty() || "[DONE]".equals(payload)) {
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(payload);
            JsonNode delta = root.path("choices").path(0).path("delta").path("content");
            return delta.isMissingNode() || delta.isNull() ? null : delta.asText();
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public String chat(List<Map<String, String>> messages, boolean jsonMode) {
        if (restClient == null) {
            return null;
        }
        // 共两次尝试：LLM 网关偶发抖动（连接重置/瞬时超时）自动恢复，
        // 两次都失败才判不可用——对上层仍是"成功或 null"，无语义变化
        String last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            last = chatOnce(messages, jsonMode);
            if (last != null) {
                return last;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String chatOnce(List<Map<String, String>> messages, boolean jsonMode) {
        try {
            Map<String, Object> resp = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(bodyOf(messages, jsonMode, false))
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

    private List<Map<String, String>> messagesOf(String systemPrompt, String userPrompt) {
        return List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt));
    }

    private Map<String, Object> bodyOf(List<Map<String, String>> messages, boolean jsonMode, boolean stream) {
        HashMap<String, Object> body = new HashMap<>();
        body.put("model", aiProperties.getModel());
        body.put("temperature", 0);
        body.put("messages", messages);
        if (jsonMode) {
            body.put("response_format", Map.of("type", "json_object"));
        }
        if (stream) {
            body.put("stream", true);
        }
        if (!aiProperties.isThinkingEnabled()) {
            // DeepSeek 思考模式标准关闭参数（思考内容在独立 reasoning_content 字段，
            // 本客户端只取 content，最终回答不受影响）
            body.put("chat_template_kwargs", Map.of("thinking", false));
        }
        return body;
    }
}

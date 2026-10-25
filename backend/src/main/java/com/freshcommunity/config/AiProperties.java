package com.freshcommunity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 解析能力配置（OpenAI 兼容接口）。
 *
 * 未配置 api-key 时视为不可用，调用方应直接回落规则解析；
 * 设置该字段不引入校验，避免无 key 环境启动失败。
 */
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    /** OpenAI 兼容接口基址，如 https://api.deepseek.com/v1 */
    private String baseUrl = "https://api.deepseek.com/v1";
    /** 供应商 API key，为空表示不可用 */
    private String apiKey = "";
    /** 模型名，如 deepseek-chat */
    private String model = "deepseek-chat";
    /** 单次调用超时（秒） */
    private long timeoutSeconds = 60;
    /** 思考模式开关：false 时请求携带 DeepSeek 标准关闭参数（思考内容不进入最终回答） */
    private boolean thinkingEnabled = false;

    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public long getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(long timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public boolean isThinkingEnabled() { return thinkingEnabled; }
    public void setThinkingEnabled(boolean thinkingEnabled) { this.thinkingEnabled = thinkingEnabled; }
}
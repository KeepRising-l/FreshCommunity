package com.freshcommunity;

import com.freshcommunity.dto.DemandCondition;
import com.freshcommunity.service.LlmParseService;
import com.freshcommunity.support.ApiTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1 LLM 解析能力：无 key 环境下 LlmParseService 标记不可用、走规则解析，
 * 应用正常启动；组合解析器（LLM 优先→规则回落）在无 key 时行为等价于规则解析。
 */
@DisplayName("AI 导购 · LLM 解析与降级")
class LlmParseServiceTest extends ApiTestBase {

    @Autowired
    private LlmParseService llmParseService;

    @Test
    @DisplayName("无 key 环境：LlmParseService 可用但回落规则，常见表述解析成功")
    void noKeyFallsBackToRule() {
        DemandCondition c = llmParseService.parse("想要 10 元以内的苹果");
        assertThat(c).isNotNull();
        assertThat(c.getKeyword()).isEqualTo("苹果");
        assertThat(c.getMaxPrice()).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("无 key 环境：非标表述解析失败返回 null（与规则实现一致）")
    void noKeyUnparseable() {
        assertThat(llmParseService.parse("？？？")).isNull();
    }

    @Test
    @DisplayName("无 key 环境：组合解析器对无价格需求也走规则，关键词正确")
    void noKeyNoPrice() {
        DemandCondition c = llmParseService.parse("想要红富士");
        assertThat(c).isNotNull();
        assertThat(c.getKeyword()).isEqualTo("红富士");
        assertThat(c.getMaxPrice()).isNull();
    }
}

package com.freshcommunity.service;

import com.freshcommunity.dto.DemandCondition;

/**
 * 需求文本解析：把用户的一句自然语言描述解析为结构化订阅条件。
 * 无法解析时返回 {@code null}（由调用方提示用户换一种说法）。
 *
 * 当前实现为规则解析（{@link RuleDemandParseService}）；
 * 后续接入 LLM 解析时新增实现类，按"LLM 优先、失败回落规则"组合，本接口不变。
 */
public interface DemandParseService {

    DemandCondition parse(String text);
}

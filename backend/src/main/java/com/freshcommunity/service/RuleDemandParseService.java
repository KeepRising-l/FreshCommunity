package com.freshcommunity.service;

import com.freshcommunity.dto.DemandCondition;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 需求解析的规则实现：覆盖"X 元以内/以下的 Y"类常见表述
 * （如"想要 10 元以内的苹果"、"20 块以下的红富士"、"便宜的苹果"）。
 *
 * 解析策略：
 * 1. 价格上限：提取文中"数字 + 元/块"（忽略"不超过/以内/以下"等修饰，语义均为上限）；
 * 2. 商品关键词：剔除价格片段、句式词（想要/帮我/有货等）与修饰词（便宜/实惠等），
 *    取剩余连续中文串为关键词；无中文关键词即视为无法解析。
 */
@Service
public class RuleDemandParseService implements DemandParseService {

    /** 价格：如"10 元"、"8.8块"、"不超过 20 元"中的数字部分 */
    private static final Pattern PRICE_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:元|块)");
    /** 连续中文串（商品关键词候选） */
    private static final Pattern ZH_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5]+");

    /** 句式词与修饰词（长的在前，保证先被剔除） */
    private static final String[] FILLER_WORDS = {
            "我想要", "想要", "我要", "想买", "帮我", "帮忙", "看一下", "看看",
            "有没有", "有货", "告诉我", "通知我", "提醒我", "叫我", "留意", "关注",
            "便宜", "实惠", "好吃", "新鲜",
            "不超过", "以内", "以下", "以里", "左右", "上下"
    };

    @Override
    public DemandCondition parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String raw = text.trim();

        BigDecimal maxPrice = null;
        Matcher priceMatcher = PRICE_PATTERN.matcher(raw);
        if (priceMatcher.find()) {
            maxPrice = new BigDecimal(priceMatcher.group(1));
        }

        String cleaned = PRICE_PATTERN.matcher(raw).replaceAll(" ");
        for (String filler : FILLER_WORDS) {
            cleaned = cleaned.replace(filler, "");
        }
        cleaned = cleaned.replaceAll("[\\s的了，,。.！!？?；;：:、]", "");

        Matcher zhMatcher = ZH_PATTERN.matcher(cleaned);
        String keyword = zhMatcher.find() ? zhMatcher.group() : "";
        if (keyword.isEmpty() || keyword.length() > 64) {
            return null;
        }
        return new DemandCondition(keyword, maxPrice);
    }
}

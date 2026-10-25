package com.freshcommunity.controller;

import com.freshcommunity.common.Result;
import com.freshcommunity.dto.AiRecommendVO;
import com.freshcommunity.service.AiRecommendService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 即时推荐：一句话需求 → 基于 在售商品/进行中团购/口碑 的推荐回答与商品卡片。
 * 与订阅提醒互补：这里回答"现在买什么"，订阅解决"以后有货叫我"。
 */
@RestController
@RequestMapping("/api/ai")
public class AiRecommendController {

    private final AiRecommendService aiRecommendService;

    public AiRecommendController(AiRecommendService aiRecommendService) {
        this.aiRecommendService = aiRecommendService;
    }

    @Data
    public static class RecommendRequest {
        @NotBlank(message = "需求不能为空")
        @Size(max = 255, message = "需求不能超过 255 字")
        private String text;
    }

    @PostMapping("/recommend")
    public Result<AiRecommendVO> recommend(@jakarta.validation.Valid @RequestBody RecommendRequest req) {
        return Result.success(aiRecommendService.recommend(req.getText().trim()));
    }
}

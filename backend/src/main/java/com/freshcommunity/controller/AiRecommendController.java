package com.freshcommunity.controller;

import com.freshcommunity.common.Result;
import com.freshcommunity.dto.AiRecommendVO;
import com.freshcommunity.service.AiRecommendService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI 即时推荐：一句话需求 → 基于 在售商品/进行中团购/口碑 的推荐回答与商品卡片。
 * 与订阅提醒互补：这里回答"现在买什么"，订阅解决"以后有货叫我"。
 *
 * stream 端点以 SSE 逐段推送正文（打字机），完成后发 done 事件携带商品卡片；
 * LLM 不可用/失败发 error 事件（前端展示可重试提示，不伪造推荐）。
 */
@RestController
@RequestMapping("/api/ai")
public class AiRecommendController {

    /** 流式生成的独立线程池：SSE emitter 需要异步写，不能占容器请求线程 */
    private static final ExecutorService STREAM_EXECUTOR = Executors.newCachedThreadPool();

    private final AiRecommendService aiRecommendService;

    public AiRecommendController(AiRecommendService aiRecommendService) {
        this.aiRecommendService = aiRecommendService;
    }

    @Data
    public static class RecommendRequest {
        @NotBlank(message = "需求不能为空")
        @Size(max = 255, message = "需求不能超过 255 字")
        private String text;
        /** 追问历史（user/assistant 对，服务端裁剪最近 4 条） */
        private List<Map<String, String>> history;
    }

    @PostMapping("/recommend")
    public Result<AiRecommendVO> recommend(@jakarta.validation.Valid @RequestBody RecommendRequest req) {
        return Result.success(aiRecommendService.recommend(req.getText().trim(), req.getHistory()));
    }

    /** 流式版：事件 delta(增量文本) → done(完整 VO) / error(可读错误)。 */
    @PostMapping(value = "/recommend/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter recommendStream(@jakarta.validation.Valid @RequestBody RecommendRequest req) {
        // 鉴权/参数错误在注册前抛出，走统一 401/400 JSON（ emitter 一旦建立就只能走 error 事件）
        aiRecommendService.requireAvailable();
        SseEmitter emitter = new SseEmitter(0L);
        String text = req.getText().trim();
        List<Map<String, String>> history = req.getHistory();

        STREAM_EXECUTOR.submit(() -> {
            try {
                AiRecommendVO vo = aiRecommendService.recommendStream(text, history, delta -> {
                    try {
                        emitter.send(SseEmitter.event().name("delta").data(delta));
                    } catch (Exception ignore) {
                        // 客户端断开：后续 send 同样失败，最终由 chatStream 完成或异常收尾
                    }
                });
                emitter.send(SseEmitter.event().name("done").data(vo));
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().name("error")
                            .data(e.getMessage() == null ? "AI 服务暂时不可用" : e.getMessage()));
                    emitter.complete();
                } catch (Exception ignore) {
                    emitter.completeWithError(e);
                }
            }
        });
        return emitter;
    }
}

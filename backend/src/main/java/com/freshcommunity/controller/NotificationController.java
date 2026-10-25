package com.freshcommunity.controller;

import com.freshcommunity.common.Result;
import com.freshcommunity.entity.Notification;
import com.freshcommunity.service.NotificationPushService;
import com.freshcommunity.service.NotificationService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationPushService pushService;

    public NotificationController(NotificationService notificationService,
                                  NotificationPushService pushService) {
        this.notificationService = notificationService;
        this.pushService = pushService;
    }

    @GetMapping
    public Result<List<Notification>> list(@RequestAttribute("userId") Long userId) {
        return Result.success(notificationService.listByUser(userId));
    }

    /**
     * 消息实时推送流（SSE）。前端用 fetch 消费（可携带 Authorization 头，token 不进 URL）；
     * 注册即回 hello，此后每条站内消息在事务提交后以 notification 事件推送。
     * 未登录由 Security 过滤链统一 401。
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestAttribute("userId") Long userId) {
        return pushService.register(userId);
    }

    @GetMapping("/unread-count")
    public Result<Long> unreadCount(@RequestAttribute("userId") Long userId) {
        return Result.success(notificationService.unreadCount(userId));
    }

    @PostMapping("/{id}/read")
    public Result<Void> markRead(@RequestAttribute("userId") Long userId, @PathVariable Long id) {
        notificationService.markRead(userId, id);
        return Result.success();
    }

    @PostMapping("/read-all")
    public Result<Void> markAllRead(@RequestAttribute("userId") Long userId) {
        notificationService.markAllRead(userId);
        return Result.success();
    }
}

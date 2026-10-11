package com.freshcommunity.controller;

import com.freshcommunity.common.Result;
import com.freshcommunity.entity.Notification;
import com.freshcommunity.service.NotificationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public Result<List<Notification>> list(@RequestAttribute("userId") Long userId) {
        return Result.success(notificationService.listByUser(userId));
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

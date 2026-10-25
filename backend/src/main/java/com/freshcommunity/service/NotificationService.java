package com.freshcommunity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.freshcommunity.entity.Notification;
import com.freshcommunity.mapper.NotificationMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationService {

    public static final String TYPE_GROUP_SUCCESS = "GROUP_SUCCESS";
    public static final String TYPE_GROUP_FAILED = "GROUP_FAILED";
    public static final String TYPE_ORDER_TIMEOUT = "ORDER_TIMEOUT";
    public static final String TYPE_PICKUP_DONE = "PICKUP_DONE";
    public static final String TYPE_REFUNDED = "REFUNDED";
    /** AI 导购：需求订阅命中提醒 */
    public static final String TYPE_SUBSCRIPTION_MATCH = "SUBSCRIPTION_MATCH";

    private final NotificationMapper notificationMapper;

    public NotificationService(NotificationMapper notificationMapper) {
        this.notificationMapper = notificationMapper;
    }

    /**
     * 统一通知入口。由各订单事件在业务事务内调用，保证"状态变更"与"消息写入"原子。
     */
    public void notify(Long userId, String type, String title, String content, Long refOrderId) {
        notify(userId, type, title, content, refOrderId, null);
    }

    /** 团购级事件入口（订阅命中提醒）：携带团购引用供前端直达团购详情。 */
    public void notifyGroupEvent(Long userId, String type, String title, String content, Long refGroupBuyId) {
        notify(userId, type, title, content, null, refGroupBuyId);
    }

    private void notify(Long userId, String type, String title, String content,
                        Long refOrderId, Long refGroupBuyId) {
        if (userId == null) {
            return;
        }
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setContent(content);
        n.setRefOrderId(refOrderId);
        n.setRefGroupBuyId(refGroupBuyId);
        n.setIsRead(0);
        notificationMapper.insert(n);
    }

    public List<Notification> listByUser(Long userId) {
        return notificationMapper.selectList(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .orderByDesc(Notification::getCreatedAt));
    }

    public long unreadCount(Long userId) {
        return notificationMapper.countUnread(userId);
    }

    /** 标记单条已读；UPDATE 带 user_id 条件，非本人消息更新行数为 0 → 拒绝。 */
    public void markRead(Long userId, Long id) {
        int updated = notificationMapper.markRead(id, userId);
        if (updated == 0) {
            throw new IllegalArgumentException("消息不存在");
        }
    }

    public void markAllRead(Long userId) {
        notificationMapper.markAllRead(userId);
    }
}

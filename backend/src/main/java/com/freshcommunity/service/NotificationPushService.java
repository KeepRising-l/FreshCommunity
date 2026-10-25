package com.freshcommunity.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 站内消息的 SSE 实时推送：每用户可有多条在线连接（多标签页），
 * 事件在业务事务提交后投递（见 NotificationService 的 afterCommit 挂接），
 * 避免读到未提交数据或推送后回滚的"幽灵消息"。
 *
 * 连接生命周期：注册即送 hello（前端据此确认链路通），30 秒心跳注释行保活，
 * 完成/超时/错误时摘除。毕设规模不做消息队列与离线补偿（重连后前端拉未读数校准）。
 */
@Slf4j
@Service
public class NotificationPushService {

    public static final long EMITTER_TIMEOUT_MS = 0L; // 不主动超时（由心跳与客户端断开管理）

    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();

    public SseEmitter register(Long userId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        List<SseEmitter> list = emittersByUser.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        emitter.onCompletion(() -> list.remove(emitter));
        emitter.onTimeout(() -> list.remove(emitter));
        emitter.onError(e -> list.remove(emitter));
        try {
            emitter.send(SseEmitter.event().name("hello").data("connected"));
        } catch (IOException e) {
            list.remove(emitter);
        }
        return emitter;
    }

    /** 向该用户全部在线连接推送消息事件；无连接时静默（拉模式兜底）。 */
    public void push(Long userId, Object eventPayload) {
        List<SseEmitter> list = emittersByUser.get(userId);
        if (list == null || list.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(eventPayload));
            } catch (Exception e) {
                list.remove(emitter);
                log.debug("推送失败，摘除连接：{}", e.getMessage());
            }
        }
    }

    /** 在线连接数（观测/测试用）。 */
    public int onlineConnections(Long userId) {
        List<SseEmitter> list = emittersByUser.get(userId);
        return list == null ? 0 : list.size();
    }
}

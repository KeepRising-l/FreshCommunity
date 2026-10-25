import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getUnreadCount } from '../api/notification'
import { createSseStream } from '../utils/sse'

/**
 * 未读数 + SSE 实时推送连接管理。
 * connect 在 Dashboard 挂载时调用（登录后）；收到 notification 事件即刷未读数，
 * 并广播给订阅者（消息中心页借此自动刷新列表）。断线由 utils/sse 自动重连，
 * 重连成功后拉一次未读数校准。
 */
export const useNotificationStore = defineStore('notification', () => {
  const unread = ref(0)
  /** 实时事件订阅者（页面级回调），key 任意字符串便于解绑 */
  const listeners = new Map()
  let stream = null
  let connecting = false

  async function refresh() {
    try {
      const res = await getUnreadCount()
      unread.value = res.data || 0
    } catch (e) {
      unread.value = 0
    }
  }

  function onEvent(key, fn) {
    listeners.set(key, fn)
    return () => listeners.delete(key)
  }

  function emitEvent(payload) {
    for (const fn of listeners.values()) {
      try {
        fn(payload)
      } catch {
        /* 页面回调异常不影响其他订阅者 */
      }
    }
  }

  function connect(token) {
    if (stream || connecting) return
    connecting = true
    stream = createSseStream({
      url: '/api/notifications/stream',
      token,
      onEvent: ({ event, data }) => {
        if (event === 'notification') {
          unread.value += 1
          emitEvent(data)
        }
      },
      onReconnect: () => refresh()
    })
    connecting = false
  }

  function disconnect() {
    if (stream) {
      stream.close()
      stream = null
    }
  }

  return { unread, refresh, connect, disconnect, onEvent }
})

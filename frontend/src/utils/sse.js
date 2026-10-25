/**
 * SSE 流消费工具。
 *
 * 用 fetch 而非原生 EventSource：EventSource 不能带 Authorization 头（token 只能进
 * URL，会落日志）；fetch 的 ReadableStream 同样能逐块读取 text/event-stream，
 * 且支持我们统一注入 JWT 的 axios 之外的鉴权方式。
 *
 * 断线自动重连（指数退避 1s→2s→4s…封顶 30s），每次重连成功回调 onReconnect
 * （调用方借此拉一次未读数，校准断线期间漏掉的事件）。
 */

export function createSseStream({ url, token, onEvent, onReconnect }) {
  let stopped = false
  let attempt = 0
  let controller = null

  async function connect() {
    while (!stopped) {
      try {
        controller = new AbortController()
        const resp = await fetch(url, {
          headers: token ? { Authorization: 'Bearer ' + token } : {},
          signal: controller.signal
        })
        if (!resp.ok || !resp.body) {
          throw new Error('stream http ' + resp.status)
        }
        attempt = 0
        if (onReconnect) onReconnect()

        await readStream(resp.body, onEvent)
        // 流正常结束（服务端关闭）→ 走重连
        if (stopped) return
        throw new Error('stream ended')
      } catch (e) {
        if (stopped || e?.name === 'AbortError') return
        const delay = Math.min(1000 * Math.pow(2, attempt), 30000)
        attempt++
        await new Promise((r) => setTimeout(r, delay))
      }
    }
  }

  connect()

  return {
    close() {
      stopped = true
      if (controller) controller.abort()
    }
  }
}

/** 解析 text/event-stream：按行拆分，`event:`/`data:` 组装成事件对象 */
async function readStream(body, onEvent) {
  const reader = body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let eventName = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })

    let idx
    while ((idx = buffer.indexOf('\n')) >= 0) {
      const line = buffer.slice(0, idx).replace(/\r$/, '')
      buffer = buffer.slice(idx + 1)

      if (line === '') {
        // 空行 = 事件结束
        continue
      }
      if (line.startsWith(':')) continue // 心跳注释行
      if (line.startsWith('event:')) {
        eventName = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        const data = line.slice(5).trim()
        let parsed = data
        try {
          parsed = JSON.parse(data)
        } catch {
          /* 非 JSON data 原样传出 */
        }
        if (onEvent) onEvent({ event: eventName || 'message', data: parsed })
        eventName = ''
      }
    }
  }
}

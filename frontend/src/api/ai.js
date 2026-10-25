import request from '../utils/request'

/**
 * AI 即时推荐（同步版）：一句话需求 → 基于在售商品/进行中团购/口碑的真实推荐回答 + 商品卡片。
 * @param {{text: string, history?: Array<{role: 'user'|'assistant', content: string}>}} data
 */
export function aiRecommend(data) {
  return request.post('/ai/recommend', data)
}

/**
 * AI 即时推荐（流式版）：POST fetch 消费 SSE——
 * onDelta(text) 逐段回调正文（打字机），onDone(vo) 携带完整结果（reply + items），
 * onError(message) 时结束。用 fetch 而非 EventSource：POST 体 + Authorization 头。
 * @returns {{abort: Function}} 可中断
 */
export function aiRecommendStream({ text, history, onDelta, onDone, onError }) {
  const controller = new AbortController()
  const token = localStorage.getItem('token')

  ;(async () => {
    try {
      const resp = await fetch('/api/ai/recommend/stream', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { Authorization: 'Bearer ' + token } : {})
        },
        body: JSON.stringify({ text, history: history || [] }),
        signal: controller.signal
      })
      if (!resp.ok || !resp.body) {
        // 非 2xx 走统一 JSON 错误结构
        let msg = 'AI 服务暂时不可用'
        try {
          const j = await resp.json()
          msg = j.msg || msg
        } catch { /* keep default */ }
        throw new Error(msg)
      }

      const reader = resp.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buffer = ''
      let eventName = ''

      const handle = (name, rawData) => {
        if (name === 'delta') {
          // delta 的 data 是纯文本片段（可能被 JSON 序列化成 "..."）
          let piece = rawData
          try {
            const parsed = JSON.parse(rawData)
            if (typeof parsed === 'string') piece = parsed
          } catch { /* 原样使用 */ }
          onDelta && onDelta(piece)
        } else if (name === 'done') {
          try {
            onDone && onDone(JSON.parse(rawData))
          } catch {
            onDone && onDone(null)
          }
        } else if (name === 'error') {
          let msg = rawData
          try {
            const parsed = JSON.parse(rawData)
            if (parsed && parsed.msg) msg = parsed.msg
            else if (typeof parsed === 'string') msg = parsed
          } catch { /* 原样 */ }
          throw new Error(msg || 'AI 服务暂时不可用')
        }
      }

      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        let idx
        while ((idx = buffer.indexOf('\n')) >= 0) {
          const line = buffer.slice(0, idx).replace(/\r$/, '')
          buffer = buffer.slice(idx + 1)
          if (line === '' || line.startsWith(':')) continue
          if (line.startsWith('event:')) {
            eventName = line.slice(6).trim()
          } else if (line.startsWith('data:')) {
            handle(eventName || 'message', line.slice(5).trim())
            eventName = ''
          }
        }
      }
    } catch (e) {
      if (e?.name === 'AbortError') return
      // 网络层错误无 body 结构，给可读默认
      onError && onError(e?.message || 'AI 服务暂时不可用，请稍后重试')
    }
  })()

  return { abort: () => controller.abort() }
}

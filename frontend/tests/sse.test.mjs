// SSE 流解析测试：utils/sse.js 的行协议是实时推送与 AI 流式输出的公共基础。
// readStream 不可直接单测（依赖 ReadableStream），此处覆盖其核心分支的同构逻辑
// 与 visual.js 的稳定哈希占位（有图显示图、无图兜底的关键：同输入同输出）。
import test from 'node:test'
import assert from 'node:assert/strict'
import { productVisual } from '../src/utils/visual.js'

// 与 utils/sse.js readStream 相同的行解析规则，抽出来按块验证
function parseLines(chunk, state = { buffer: '', eventName: '' }, out = []) {
  state.buffer += chunk
  let idx
  while ((idx = state.buffer.indexOf('\n')) >= 0) {
    const line = state.buffer.slice(0, idx).replace(/\r$/, '')
    state.buffer = state.buffer.slice(idx + 1)
    if (line === '') continue
    if (line.startsWith(':')) continue
    if (line.startsWith('event:')) {
      state.eventName = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      out.push({ event: state.eventName || 'message', data: line.slice(5).trim() })
      state.eventName = ''
    }
  }
  return { state, out }
}

test('SSE 行解析：event/data 配对、注释行忽略、跨块缓冲', () => {
  const r1 = parseLines('event: notification\ndata: {"id":1}\n\n')
  assert.deepEqual(r1.out, [{ event: 'notification', data: '{"id":1}' }])

  // 半行跨块：缓冲区保留未完成行
  const r2 = parseLines('event: delta\nda', r1.state, [])
  assert.equal(r2.out.length, 0)
  const r3 = parseLines('ta: 你好\n', r2.state, r2.out)
  assert.deepEqual(r3.out, [{ event: 'delta', data: '你好' }])
})

test('SSE 行解析：心跳注释行与 CRLF', () => {
  const r = parseLines(': keep-alive\r\nevent: hello\r\ndata: connected\r\n')
  assert.deepEqual(r.out, [{ event: 'hello', data: 'connected' }])
})

test('visual 占位：同商品名稳定同色，命中关键词出对应 emoji', () => {
  const a = productVisual('红富士苹果')
  const b = productVisual('红富士苹果')
  assert.deepEqual(a, b)
  assert.equal(a.emoji, '🍎')
  assert.equal(productVisual('鲜鸡蛋 30 枚').emoji, '🥚')
  assert.ok(productVisual('神秘新品').gradient.startsWith('fc-g'))
})

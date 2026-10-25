// 前端纯函数测试：Node 自带 test runner（node --test），零新增依赖。
// 覆盖展示层格式化工具的关键口径（金额两位小数、倒计时紧迫态、进度上限等）。
import test from 'node:test'
import assert from 'node:assert/strict'
import {
  formatMoney,
  formatPrice,
  toDate,
  remaining,
  fromNow,
  progressPercent,
  remainingMembers,
  splitCode
} from '../src/utils/format.js'

test('formatMoney 固定两位小数', () => {
  assert.equal(formatMoney(12.9), '12.90')
  assert.equal(formatMoney(0), '0.00')
  assert.equal(formatMoney('8.505'), '8.51')
  assert.equal(formatMoney(Number.NaN), '0.00')
})

test('formatPrice 带货币符号', () => {
  assert.equal(formatPrice(19.9), '¥19.90')
})

test('toDate 非法输入返回 null（LocalDateTime 无时区串按本地解析）', () => {
  assert.equal(toDate(''), null)
  assert.equal(toDate(null), null)
  assert.equal(toDate('not-a-date-x'), null)
  const d = toDate('2026-10-26T10:30:00')
  assert.ok(d instanceof Date && !Number.isNaN(d.getTime()))
})

test('remaining：过期 / 紧迫态（≤2h）/ 正常时长文案', () => {
  const now = Date.now()
  assert.equal(remaining('2000-01-01T00:00:00', now).expired, true)
  const urgent = remaining(new Date(now + 60 * 60 * 1000).toISOString(), now)
  assert.equal(urgent.expired, false)
  assert.equal(urgent.urgent, true)
  assert.ok(urgent.text.includes('1 小时'))
  const far = remaining(new Date(now + 3 * 24 * 3600 * 1000).toISOString(), now)
  assert.equal(far.urgent, false)
  assert.ok(far.text.includes('3 天'))
})

test('fromNow 相对时间', () => {
  const now = Date.now()
  assert.equal(fromNow(new Date(now - 30 * 1000).toISOString(), now), '刚刚')
  assert.equal(fromNow(new Date(now - 5 * 60 * 1000).toISOString(), now), '5 分钟前')
  assert.equal(fromNow(new Date(now - 3 * 3600 * 1000).toISOString(), now), '3 小时前')
})

test('progressPercent 上限 100 且空参安全', () => {
  assert.equal(progressPercent(2, 3), 67)
  assert.equal(progressPercent(5, 3), 100)
  assert.equal(progressPercent(0, 0), 0)
})

test('remainingMembers 差额不为负', () => {
  assert.equal(remainingMembers(2, 3), 1)
  assert.equal(remainingMembers(5, 3), 0)
})

test('splitCode 拆字符（取货码逐位展示）', () => {
  assert.deepEqual(splitCode('368772'), ['3', '6', '8', '7', '7', '2'])
  assert.deepEqual(splitCode(null), [])
})

/**
 * 展示层格式化工具。
 * 后端返回的 LocalDateTime 会被序列化成 "2026-10-11T14:22:33" 这样的无时区字符串，
 * JS 的 new Date() 对「日期+时间但无时区」的字符串按本地时间解析，正好符合预期。
 */

/** 安全转 Date，非法输入返回 null */
export function toDate(value) {
  if (!value) return null
  const d = value instanceof Date ? value : new Date(value)
  return Number.isNaN(d.getTime()) ? null : d
}

const pad = (n) => String(n).padStart(2, '0')

/** 金额：12.9 → "12.90" */
export function formatMoney(value) {
  const n = Number(value)
  if (!Number.isFinite(n)) return '0.00'
  return n.toFixed(2)
}

/** 带符号金额：12.9 → "¥12.90" */
export function formatPrice(value) {
  return '¥' + formatMoney(value)
}

/** "10-11 14:22" */
export function formatDateTime(value) {
  const d = toDate(value)
  if (!d) return ''
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** "2026-10-11" */
export function formatDate(value) {
  const d = toDate(value)
  if (!d) return ''
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/**
 * 剩余时长（用于团购截止倒计时）。
 * @param {string|Date} deadline 截止时间
 * @param {number} [now] 当前时间戳；传入响应式的值可让倒计时自动刷新
 */
export function remaining(deadline, now) {
  const d = toDate(deadline)
  if (!d) return { ms: 0, expired: true, text: '已截止', urgent: false }

  const ms = d.getTime() - (now || Date.now())
  if (ms <= 0) return { ms: 0, expired: true, text: '已截止', urgent: false }

  const totalMin = Math.floor(ms / 60000)
  const days = Math.floor(totalMin / 1440)
  const hours = Math.floor((totalMin % 1440) / 60)
  const mins = totalMin % 60

  let text
  if (days > 0) text = `${days} 天 ${hours} 小时`
  else if (hours > 0) text = `${hours} 小时 ${mins} 分`
  else text = `${Math.max(mins, 1)} 分钟`

  // 最后 2 小时进入紧迫态（design-spec §2 规则 3）
  return { ms, expired: false, text: `还剩 ${text}`, urgent: ms <= 2 * 3600 * 1000 }
}

/**
 * 相对时间："刚刚 / 12 分钟前 / 3 小时前 / 2 天前 / 10-05 19:00"
 * @param {string|Date} value 时间
 * @param {number} [now] 当前时间戳
 */
export function fromNow(value, now) {
  const d = toDate(value)
  if (!d) return ''
  const diff = (now || Date.now()) - d.getTime()
  if (diff < 0) return formatDateTime(value)

  const mins = Math.floor(diff / 60000)
  if (mins < 1) return '刚刚'
  if (mins < 60) return `${mins} 分钟前`

  const hours = Math.floor(mins / 60)
  if (hours < 24) return `${hours} 小时前`

  const days = Math.floor(hours / 24)
  if (days < 7) return `${days} 天前`

  return formatDateTime(value)
}

/** 参团进度百分比，0–100 的整数 */
export function progressPercent(memberCount, targetCount) {
  const m = Number(memberCount) || 0
  const t = Number(targetCount) || 0
  if (t <= 0) return 0
  return Math.min(100, Math.round((m / t) * 100))
}

/** 还差几人成团（已达标返回 0） */
export function remainingMembers(memberCount, targetCount) {
  const gap = (Number(targetCount) || 0) - (Number(memberCount) || 0)
  return gap > 0 ? gap : 0
}

/** 取货码拆成字符数组，便于逐位展示 */
export function splitCode(code) {
  return String(code || '').split('')
}

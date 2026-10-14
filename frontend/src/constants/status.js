/**
 * 状态映射的唯一来源。
 *
 * 后端状态取值（见 entity 注释与 service 常量）：
 *   团购 GroupBuy.status   : ONGOING / SUCCESS / FAIL
 *   订单 Order.status      : PENDING_PAYMENT / PENDING_GROUP / SUCCESS / PENDING_PICKUP / COMPLETED / CANCELLED / REFUNDED
 *   消息 Notification.type : GROUP_SUCCESS / GROUP_FAILED / ORDER_TIMEOUT / PICKUP_DONE / REFUNDED / SUBSCRIPTION_MATCH
 *
 * 配色语义（与 design-spec.md §5「状态徽章」一致）：
 *   orange 需要你立即行动（含倒计时）  blue 进行中 / 等待
 *   green  好消息 / 可行动              gray 终态中性      red 异常
 */

/** 徽章配色 tone，对应 StatusBadge 的样式 */
export const TONE = {
  GREEN: 'green',
  ORANGE: 'orange',
  BLUE: 'blue',
  GRAY: 'gray',
  RED: 'red',
  YELLOW: 'yellow',
  PURPLE: 'purple'
}

/** 团购状态 */
export const GROUP_BUY_STATUS = {
  ONGOING: { text: '进行中', tone: TONE.BLUE },
  SUCCESS: { text: '已成团', tone: TONE.GREEN },
  FAIL: { text: '已解散', tone: TONE.GRAY }
}

/** 订单状态 */
export const ORDER_STATUS = {
  PENDING_PAYMENT: { text: '待支付', tone: TONE.ORANGE },
  PENDING_GROUP: { text: '待成团', tone: TONE.BLUE },
  SUCCESS: { text: '已成团', tone: TONE.GREEN },
  PENDING_PICKUP: { text: '待取货', tone: TONE.GREEN },
  COMPLETED: { text: '已完成', tone: TONE.GRAY },
  CANCELLED: { text: '已取消', tone: TONE.GRAY },
  REFUNDED: { text: '已退款', tone: TONE.RED }
}

/** 订单状态筛选项（顺序即展示顺序） */
export const ORDER_STATUS_TABS = [
  { value: '', label: '全部' },
  { value: 'PENDING_PAYMENT', label: '待支付' },
  { value: 'PENDING_GROUP', label: '待成团' },
  { value: 'PENDING_PICKUP', label: '待取货' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'CANCELLED', label: '已取消' },
  { value: 'REFUNDED', label: '已退款' }
]

/** 站内消息类型 */
export const NOTIFICATION_TYPE = {
  GROUP_SUCCESS: { text: '团购已成团', icon: '🎉', tone: TONE.GREEN },
  GROUP_FAILED: { text: '团购已解散', icon: '↩️', tone: TONE.RED },
  ORDER_TIMEOUT: { text: '订单已关闭', icon: '⏰', tone: TONE.ORANGE },
  PICKUP_DONE: { text: '核销成功', icon: '✅', tone: TONE.BLUE },
  REFUNDED: { text: '退款到账', icon: '💰', tone: TONE.RED },
  SUBSCRIPTION_MATCH: { text: 'AI 导购提醒', icon: '🤖', tone: TONE.PURPLE }
}

/** 取默认值，避免出现未知状态时页面空白 */
const FALLBACK = { text: '未知', tone: TONE.GRAY }

function pick(map, key) {
  return map[key] || { ...FALLBACK, text: key || '未知' }
}

export const groupBuyStatus = (key) => pick(GROUP_BUY_STATUS, key)
export const orderStatus = (key) => pick(ORDER_STATUS, key)
export const notificationType = (key) => pick(NOTIFICATION_TYPE, key)

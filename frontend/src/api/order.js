import request from '../utils/request'

export function createOrder(groupBuyId, quantity) {
  return request.post('/orders', null, { params: { groupBuyId, quantity } })
}

export function payOrder(id) {
  return request.post('/orders/' + id + '/pay')
}

export function cancelOrder(id) {
  return request.post('/orders/' + id + '/cancel')
}

export function listMyOrders() {
  return request.get('/orders/mine')
}

/**
 * 订单详情（本人或管理员）。VO 自带商品/团购/团长/提货点展示字段。
 * @param {number|string} id 订单 id
 */
export function getOrder(id) {
  return request.get('/orders/' + id)
}

/**
 * 核销取货码。
 * @param {string} code 6 位取货码
 * @param {{skipErrorToast?: boolean}} [options] 页面内自带结果条时传 skipErrorToast，
 *   避免与全局 toast 重复提示
 */
export function verifyPickupCode(code, options = {}) {
  return request.post('/orders/verify', null, { params: { pickupCode: code }, ...options })
}

/**
 * 团长本人在自己团购下的最近 10 条核销记录（后端固定条数，不接分页参数）。
 * @returns {Promise<{data: Array<{orderNo: string, groupBuyTitle: string, quantity: number,
 *   amount: number, verifiedAt: string}>}>}
 */
export function listVerifyHistory() {
  return request.get('/orders/verify-history')
}

/** 管理端订单 CSV 导出地址（带当前筛选，后端加 BOM，Excel 直开） */
export function adminOrdersExportUrl(status, keyword) {
  const params = new URLSearchParams()
  if (status) params.set('status', status)
  if (keyword) params.set('keyword', keyword)
  const qs = params.toString()
  return '/api/admin/orders/export' + (qs ? '?' + qs : '')
}

export function refundOrder(id) {
  return request.post('/orders/' + id + '/refund')
}

/**
 * 管理端全量订单列表（ADMIN）：status 等值筛选，keyword 匹配订单号或买家用户名。
 * @param {{status?: string, keyword?: string, page?: number, size?: number}} [params]
 */
export function listAdminOrders(params = {}) {
  return request.get('/admin/orders', { params })
}

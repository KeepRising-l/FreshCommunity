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

export function refundOrder(id) {
  return request.post('/orders/' + id + '/refund')
}

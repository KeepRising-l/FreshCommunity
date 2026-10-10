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

export function verifyPickupCode(code) {
  return request.post('/orders/verify', null, { params: { pickupCode: code } })
}

export function refundOrder(id) {
  return request.post('/orders/' + id + '/refund')
}

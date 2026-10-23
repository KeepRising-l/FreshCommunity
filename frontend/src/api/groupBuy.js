import request from '../utils/request'

export function listGroupBuys() {
  return request.get('/group-buys')
}

export function listMyGroupBuys() {
  return request.get('/group-buys/mine')
}

export function getGroupBuyDetail(id) {
  return request.get('/group-buys/' + id)
}

export function createGroupBuy(data) {
  return request.post('/group-buys', data)
}

export function listGroupBuyOrders(id) {
  return request.get('/group-buys/' + id + '/orders')
}

/**
 * 团长经营概览。
 * @returns {Promise<{data: {ongoingGroups: number, pendingPickupOrders: number,
 *   completedOrders: number, totalGmv: number}}>}
 */
export function getMyGroupBuySummary() {
  return request.get('/group-buys/mine/summary')
}

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

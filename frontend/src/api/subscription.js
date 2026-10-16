import request from '../utils/request'

export function createSubscription(text, condition = {}) {
  return request.post('/subscriptions', { text, ...condition })
}

export function previewSubscription(text) {
  return request.post('/subscriptions/parse', { text })
}

export function listSubscriptions() {
  return request.get('/subscriptions')
}

export function cancelSubscription(id) {
  return request.delete('/subscriptions/' + id)
}

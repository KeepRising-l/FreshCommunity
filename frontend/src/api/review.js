import request from '../utils/request'

export function submitReview(data) {
  return request.post('/reviews', data)
}

export function listMyReviews() {
  return request.get('/reviews/mine')
}

export function getProductReviews(productId) {
  return request.get('/reviews/product/' + productId)
}

import request from '../utils/request'

export function submitReview(data) {
  return request.post('/reviews', data)
}

export function listMyReviews() {
  return request.get('/reviews/mine')
}

/** 团长本人名下的评价列表（评价回复页） */
export function listLeaderReviews() {
  return request.get('/reviews/leader/mine')
}

/** 团长回复评价（一层，可修改） */
export function replyReview(id, content) {
  return request.post(`/reviews/${id}/reply`, { content })
}

export function getProductReviews(productId) {
  return request.get('/reviews/product/' + productId)
}

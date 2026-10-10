import request from '../utils/request'

export function listProducts() {
  return request.get('/products')
}

export function createProduct(data) {
  return request.post('/products', data)
}

export function updateProduct(id, data) {
  return request.put('/products/' + id, data)
}

export function deleteProduct(id) {
  return request.delete('/products/' + id)
}

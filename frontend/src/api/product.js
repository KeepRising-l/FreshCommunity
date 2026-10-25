import request from '../utils/request'

/**
 * 在售商品检索（服务端过滤 + 分页）。
 * @param {{keyword?: string, category?: string, sort?: 'latest'|'price_asc'|'price_desc',
 *   page?: number, size?: number}} [params]
 * @returns {Promise<{data: {records: Array, total: number, current: number, size: number, pages: number}}>}
 */
export function listProducts(params = {}) {
  return request.get('/products', { params })
}

/** 在售商品的分类去重列表（升序），供筛选 chips */
export function listCategories() {
  return request.get('/products/categories')
}

/**
 * 管理端全量商品列表（含下架商品，ADMIN）。
 * @param {{keyword?: string, status?: number, page?: number, size?: number}} [params]
 */
export function adminListProducts(params = {}) {
  return request.get('/products/admin-list', { params })
}

export function getProduct(id) {
  return request.get('/products/' + id)
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

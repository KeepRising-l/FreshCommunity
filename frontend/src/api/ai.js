import request from '../utils/request'

/**
 * AI 即时推荐：一句话需求 → 基于在售商品/进行中团购/口碑的真实推荐回答 + 商品卡片。
 * @param {{text: string}} data 需求描述（≤255 字）
 * @returns {Promise<{data: {reply: string, items: Array}>}}
 */
export function aiRecommend(data) {
  return request.post('/ai/recommend', data)
}

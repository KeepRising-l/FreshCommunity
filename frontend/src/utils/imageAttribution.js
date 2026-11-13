// 仅对已核对来源的内置演示图展示说明；用户上传的图片不使用这些署名。
const attributedImages = new Set(['p06', 'p11', 'p13', 'p14', 'p28', 'p33', 'p34', 'p35', 'p36', 'p40', 'p42'])

export function seedImageAttribution(imageUrl) {
  if (typeof imageUrl !== 'string') return null
  const match = /^\/images\/products\/(p\d{2})\.jpg(?:[?#].*)?$/.exec(imageUrl)
  return match && attributedImages.has(match[1]) ? `/images/attribution.html#${match[1]}` : null
}

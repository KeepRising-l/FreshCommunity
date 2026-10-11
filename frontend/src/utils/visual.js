/**
 * 商品视觉占位。
 *
 * 说明：后端 GroupBuyListVO / Order 都不带商品图片字段，前端目前拿不到图片 URL。
 * 在补齐图片能力之前，用「关键词 → emoji + 渐变底色」生成稳定可辨识的占位，
 * 避免列表出现空白图位。补齐后只需把 productVisual 换成返回真实图片地址即可。
 */

const RULES = [
  [/番茄|西红柿|圣女果/, '🍅'],
  [/草莓/, '🍓'],
  [/鸡蛋|蛋/, '🥚'],
  [/牛奶|酸奶|鲜奶|奶/, '🥛'],
  [/三文鱼|鱼|虾|蟹|海鲜/, '🐟'],
  [/大米|米|稻花香/, '🍚'],
  [/娃娃菜|白菜|青菜|菠菜|生菜|蔬菜|西兰花|菜/, '🥬'],
  [/苹果/, '🍎'],
  [/橙|橘|柑/, '🍊'],
  [/香蕉/, '🍌'],
  [/葡萄|提子/, '🍇'],
  [/西瓜/, '🍉'],
  [/土豆|萝卜|南瓜|红薯/, '🥔'],
  [/猪肉|牛肉|羊肉|排骨|肉/, '🥩'],
  [/面包|馒头|面/, '🍞'],
  [/油|酱|醋|调料/, '🫙']
]

const GRADIENTS = ['fc-g1', 'fc-g2', 'fc-g3', 'fc-g4', 'fc-g5', 'fc-g6']

/** 简单稳定哈希，保证同一标题每次都拿到同一个渐变 */
function hash(str) {
  let h = 0
  for (let i = 0; i < str.length; i++) {
    h = (h * 31 + str.charCodeAt(i)) | 0
  }
  return Math.abs(h)
}

/**
 * @param {string} title 商品 / 团购标题
 * @returns {{ emoji: string, gradient: string }}
 */
export function productVisual(title) {
  const text = String(title || '')
  let emoji = '🧺'
  for (const [re, e] of RULES) {
    if (re.test(text)) {
      emoji = e
      break
    }
  }
  return { emoji, gradient: GRADIENTS[hash(text) % GRADIENTS.length] }
}

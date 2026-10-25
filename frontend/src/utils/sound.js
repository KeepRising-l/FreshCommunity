/**
 * 操作反馈提示音：Web Audio 合成，无音频资产依赖。
 * 浏览器自动播放策略要求用户先与页面交互过——核销/下单都是点击触发，天然满足。
 */

let ctx = null

function audioCtx() {
  if (!ctx) {
    const AC = window.AudioContext || window.webkitAudioContext
    if (!AC) return null
    ctx = new AC()
  }
  if (ctx.state === 'suspended') ctx.resume()
  return ctx
}

/** 单音：frequency(Hz) duration(秒) delay(秒后起音) */
function tone(frequency, duration, delay = 0, type = 'sine', gain = 0.08) {
  const ac = audioCtx()
  if (!ac) return
  const osc = ac.createOscillator()
  const g = ac.createGain()
  osc.type = type
  osc.frequency.value = frequency
  const start = ac.currentTime + delay
  g.gain.setValueAtTime(0.0001, start)
  g.gain.exponentialRampToValueAtTime(gain, start + 0.02)
  g.gain.exponentialRampToValueAtTime(0.0001, start + duration)
  osc.connect(g).connect(ac.destination)
  osc.start(start)
  osc.stop(start + duration + 0.05)
}

/** 成功：上行双音（叮-咚） */
export function playSuccessSound() {
  tone(660, 0.12)
  tone(880, 0.18, 0.12)
}

/** 失败：下行低音 */
export function playErrorSound() {
  tone(330, 0.2, 0, 'square', 0.05)
  tone(220, 0.25, 0.18, 'square', 0.05)
}

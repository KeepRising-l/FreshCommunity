import { ref, onMounted, onUnmounted } from 'vue'

/**
 * 全局共享的「当前时间」。
 *
 * 倒计时（团购截止、订单支付超时）与相对时间（消息"12 分钟前"）都需要随时间刷新。
 * 与其让每个卡片各起一个定时器，不如共用一个：无论页面上有多少张卡片，
 * 始终只有一个 30 秒的 interval。
 *
 * 用法：
 *   const now = useNow()
 *   const left = computed(() => remaining(props.group.deadline, now.value))
 */
const now = ref(Date.now())
let timer = null
let refCount = 0

function start() {
  if (timer) return
  timer = setInterval(() => {
    now.value = Date.now()
  }, 30 * 1000)
}

function stop() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

export function useNow() {
  onMounted(() => {
    refCount += 1
    start()
  })

  onUnmounted(() => {
    refCount -= 1
    if (refCount <= 0) {
      refCount = 0
      stop()
    }
  })

  return now
}

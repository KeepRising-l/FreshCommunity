<template>
  <article class="order-card">
    <!-- 头部：订单号 + 时间 + 状态 -->
    <header class="head">
      <span class="no">{{ order.orderNo }}</span>
      <span class="time">{{ formatDateTime(order.createdAt) }} 下单</span>
      <StatusBadge class="st" kind="order" :status="order.status" />
    </header>

    <!-- 主体：商品 + 金额 -->
    <div class="main">
      <div class="thumb" :class="visual.gradient">{{ visual.emoji }}</div>
      <div class="info">
        <h4>{{ title }}</h4>
        <div v-if="pickupText" class="from">
          <el-icon><LocationInformation /></el-icon>
          <span>{{ pickupText }}</span>
        </div>
      </div>
      <div class="amt">
        <b class="fc-num">{{ formatPrice(order.amount) }}</b>
        <span>× {{ order.quantity }} 件</span>
      </div>
    </div>

    <!-- 取货码票据 -->
    <div v-if="showCode" class="pickup">
      <div>
        <div class="pickup-label">取货码</div>
        <div class="code">
          <i v-for="(c, i) in codeChars" :key="i">{{ c }}</i>
        </div>
      </div>
      <div class="pickup-text">
        <b>{{ order.status === 'PENDING_PICKUP' ? '凭码到店自提' : '成团后凭码自提' }}</b>
        <span>{{ pickupText || '请向团长出示取货码核销' }}</span>
      </div>
    </div>

    <!-- 底部：提示 + 操作 -->
    <footer class="foot">
      <span class="hint">
        <template v-if="hint">{{ hint }}</template>
        <template v-else>—</template>
      </span>
      <template v-if="showActions">
        <el-button v-if="canCancel" size="small" @click="$emit('cancel', order)">取消订单</el-button>
        <el-button v-if="canPay" size="small" type="warning" @click="$emit('pay', order)">立即支付</el-button>
        <el-button v-if="canReview" size="small" type="primary" @click="$emit('review', order)">去评价</el-button>
        <el-button v-if="canViewCode" size="small" type="primary" @click="$emit('show-code', order)">出示取货码</el-button>
        <span v-if="isReviewed" class="reviewed">已评价</span>
      </template>
    </footer>
  </article>
</template>

<script setup>
import { computed } from 'vue'
import { LocationInformation } from '@element-plus/icons-vue'
import StatusBadge from './StatusBadge.vue'
import { useNow } from '../composables/useNow'
import { productVisual } from '../utils/visual'
import { formatPrice, formatDateTime, splitCode } from '../utils/format'

const props = defineProps({
  /** 后端 Order 实体：{ id, orderNo, quantity, amount, status, pickupCode, createdAt, reviewed, ... } */
  order: { type: Object, required: true },
  /** 后端订单接口暂不返回商品名与提货点，由调用方按需传入 */
  productTitle: { type: String, default: '' },
  pickupPointName: { type: String, default: '' },
  showActions: { type: Boolean, default: true }
})

defineEmits(['pay', 'cancel', 'review', 'show-code'])

const now = useNow()

/** 订单超时关单窗口（与后端 ScheduledJobs 的 30 分钟一致） */
const PAY_WINDOW_MS = 30 * 60 * 1000

const title = computed(() => props.productTitle || `订单 ${props.order.orderNo}`)

const visual = computed(() =>
  props.productTitle
    ? productVisual(props.productTitle)
    : { emoji: '🧺', gradient: 'fc-g3' }
)

const pickupText = computed(() => props.pickupPointName || '')

const codeChars = computed(() => splitCode(props.order.pickupCode))

const showCode = computed(
  () => !!props.order.pickupCode && ['SUCCESS', 'PENDING_PICKUP'].includes(props.order.status)
)

const canPay = computed(() => props.order.status === 'PENDING_PAYMENT')
const canCancel = computed(() => ['PENDING_PAYMENT', 'PENDING_GROUP'].includes(props.order.status))
const canReview = computed(() => props.order.status === 'COMPLETED' && !props.order.reviewed)
const isReviewed = computed(() => props.order.status === 'COMPLETED' && !!props.order.reviewed)
const canViewCode = computed(() => props.order.status === 'PENDING_PICKUP' && !!props.order.pickupCode)

/** 待支付：剩余支付时间；其余状态给出状态说明 */
const hint = computed(() => {
  const s = props.order.status

  if (s === 'PENDING_PAYMENT') {
    const created = new Date(props.order.createdAt).getTime()
    if (Number.isFinite(created)) {
      const left = created + PAY_WINDOW_MS - now.value
      if (left > 0) {
        const mins = Math.floor(left / 60000)
        const secs = Math.floor((left % 60000) / 1000)
        return `⏰ 请在 ${mins} 分 ${String(secs).padStart(2, '0')} 秒内完成支付，超时订单将自动关闭`
      }
      return '⏰ 支付时间已过，订单即将被系统关闭'
    }
    return '⏰ 请尽快完成支付，超时订单将自动关闭'
  }

  if (s === 'PENDING_GROUP') return '⏳ 等待其他邻居参团，达标后自动成团'
  if (s === 'SUCCESS') return '🎉 已成团，团长正在备货，请留意取货通知'
  if (s === 'PENDING_PICKUP') return '✅ 已备货，请凭取货码到提货点自提'
  if (s === 'COMPLETED') return props.order.reviewed ? '✅ 交易完成，感谢你的评价' : '✅ 交易完成，欢迎评价本次体验'
  if (s === 'CANCELLED') return '💬 订单已取消'
  if (s === 'REFUNDED') return '💬 团购未达标已解散，款项已全额退回'
  return ''
})
</script>

<style scoped>
.order-card {
  background: var(--fc-card);
  border: 1px solid var(--fc-line);
  border-radius: var(--fc-r-lg);
  box-shadow: var(--fc-sh-1);
  overflow: hidden;
  margin-bottom: 14px;
}

.head {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 13px 20px;
  background: var(--fc-card-2);
  border-bottom: 1px solid var(--fc-line-soft);
  font-size: 12.5px;
  color: var(--fc-ink-400);
}

.no {
  font-family: ui-monospace, Menlo, Consolas, monospace;
  color: var(--fc-ink-500);
  font-size: 12px;
}

.st {
  margin-left: auto;
}

.main {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 17px 20px;
}

.thumb {
  width: 64px;
  height: 64px;
  border-radius: 14px;
  flex: none;
  display: grid;
  place-items: center;
  font-size: 30px;
}

.info {
  flex: 1;
  min-width: 0;
}

.info h4 {
  margin: 0 0 5px;
  font-size: 14.5px;
  font-weight: 650;
  color: var(--fc-ink-900);
}

.from {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--fc-ink-400);
  font-size: 12.5px;
  min-width: 0;
}

.from span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.amt {
  text-align: right;
  flex: none;
}

.amt b {
  font-size: 17px;
  font-weight: 750;
  color: var(--fc-ink-900);
}

.amt span {
  display: block;
  font-size: 11.5px;
  color: var(--fc-ink-400);
}

.pickup {
  margin: 0 20px 16px;
  border-radius: var(--fc-r);
  padding: 16px 18px;
  background: linear-gradient(100deg, #EAF7F0, #F6FCF8);
  border: 1.5px dashed #A9DCBE;
  display: flex;
  align-items: center;
  gap: 18px;
}

.pickup-label {
  font-size: 11.5px;
  color: var(--fc-ink-400);
  font-weight: 600;
  margin-bottom: 6px;
}

.code {
  display: flex;
  gap: 7px;
}

.code i {
  width: 38px;
  height: 46px;
  border-radius: 9px;
  background: #fff;
  border: 1px solid var(--fc-brand-100);
  display: grid;
  place-items: center;
  font-style: normal;
  font-size: 21px;
  font-weight: 750;
  color: var(--fc-brand-700);
  font-family: ui-monospace, Menlo, Consolas, monospace;
  box-shadow: var(--fc-sh-1);
}

.pickup-text {
  flex: 1;
  min-width: 0;
}

.pickup-text b {
  display: block;
  font-size: 13.5px;
  color: var(--fc-ink-800);
  font-weight: 650;
  margin-bottom: 3px;
}

.pickup-text span {
  font-size: 12px;
  color: var(--fc-ink-400);
}

.foot {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 13px 20px;
  border-top: 1px solid var(--fc-line-soft);
}

.hint {
  font-size: 12.5px;
  color: var(--fc-ink-400);
  margin-right: auto;
  line-height: 1.5;
}

.reviewed {
  color: var(--fc-ink-400);
  font-size: 12px;
}
</style>

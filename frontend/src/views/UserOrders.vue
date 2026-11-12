<template>
  <div class="orders">
    <header class="page-head">
      <div>
        <h2>我的订单</h2>
        <p>从参团到提货，随时查看订单进度。</p>
      </div>
      <span v-if="!loading && orders.length" class="order-count">{{ orders.length }} 笔订单</span>
    </header>
    <!-- 状态筛选 -->
    <div class="tabs">
      <button
        v-for="t in tabs"
        :key="t.value"
        :class="{ on: status === t.value }"
        @click="status = t.value"
      >
        {{ t.label }}
        <span v-if="t.count" class="n">{{ t.count }}</span>
      </button>
    </div>

    <SkeletonList v-if="loading" :rows="3" variant="card" />

    <EmptyState
      v-else-if="visible.length === 0"
      icon="🧾"
      :title="orders.length === 0 ? '还没有订单' : '这个状态下没有订单'"
      :description="orders.length === 0 ? '去团购广场看看邻居在拼什么吧。' : '换个状态筛选试试。'"
      :action-text="orders.length === 0 ? '去团购广场' : '查看全部订单'"
      @action="orders.length === 0 ? router.push('/user/groups') : (status = '')"
    />

    <div v-else class="list">
      <OrderCard
        v-for="o in visible"
        :key="o.id"
        :order="o"
        :product-title="o.productName || ''"
        :pickup-point-name="o.pickupPointName || ''"
        @pay="pay"
        @cancel="cancel"
        @review="openReview(o)"
        @show-code="showCode"
        @open="goDetail"
      />
    </div>

    <!-- 评价弹窗（公共组件，订单详情页共用） -->
    <ReviewDialog v-model:visible="reviewVisible" :order="reviewTarget" @submitted="load" />

    <!-- 取货码弹窗 -->
    <el-dialog v-model="codeVisible" title="取货凭证" width="min(400px, calc(100vw - 32px))">
      <div v-if="codeOrder" class="ticket">
        <div class="ticket-head">
          <div class="ticket-ok">
            <el-icon><CircleCheckFilled /></el-icon>
          </div>
          <b>{{ codeOrder.productName || '你的商品' }}</b>
          <span>数量 × {{ codeOrder.quantity }}</span>
        </div>

        <div class="ticket-code">
          <i v-for="(c, i) in splitCode(codeOrder.pickupCode)" :key="i">{{ c }}</i>
        </div>
        <p class="ticket-tip">到提货点向团长出示此码，或让其输入核销</p>

        <div class="ticket-kv">
          <span class="k">订单号</span>
          <span class="v">{{ codeOrder.orderNo }}</span>
        </div>
        <div class="ticket-kv">
          <span class="k">金　额</span>
          <span class="v">{{ formatPrice(codeOrder.amount) }}</span>
        </div>
        <div class="ticket-kv">
          <span class="k">下　单</span>
          <span class="v">{{ formatDateTime(codeOrder.createdAt) }}</span>
        </div>
      </div>
      <template #footer>
        <el-button type="primary" @click="codeVisible = false">我知道了</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { CircleCheckFilled } from '@element-plus/icons-vue'
import OrderCard from '../components/OrderCard.vue'
import ReviewDialog from '../components/ReviewDialog.vue'
import SkeletonList from '../components/SkeletonList.vue'
import EmptyState from '../components/EmptyState.vue'
import { listMyOrders, payOrder, cancelOrder } from '../api/order'
import { ORDER_STATUS_TABS } from '../constants/status'
import { formatPrice, formatDateTime, splitCode } from '../utils/format'

const router = useRouter()

const orders = ref([])
const loading = ref(true)
const status = ref('')

const reviewVisible = ref(false)
const reviewTarget = ref(null)

const codeVisible = ref(false)
const codeOrder = ref(null)

const tabs = computed(() =>
  ORDER_STATUS_TABS.map((t) => ({
    ...t,
    count: t.value ? orders.value.filter((o) => o.status === t.value).length : orders.value.length
  }))
)

const visible = computed(() =>
  status.value ? orders.value.filter((o) => o.status === status.value) : orders.value
)

/** 订单 VO 已自带商品/提货点字段，不再拉全量商品做映射 */
async function load() {
  loading.value = true
  try {
    const res = await listMyOrders()
    orders.value = res.data || []
  } catch (e) {
    orders.value = []
  } finally {
    loading.value = false
  }
}

async function pay(order) {
  await payOrder(order.id)
  ElMessage.success('支付成功')
  load()
}

async function cancel(order) {
  await cancelOrder(order.id)
  ElMessage.success('取消成功')
  load()
}

function openReview(order) {
  reviewTarget.value = order
  reviewVisible.value = true
}

function showCode(order) {
  codeOrder.value = order
  codeVisible.value = true
}

function goDetail(order) {
  router.push('/user/orders/' + order.id)
}

onMounted(load)
</script>

<style scoped>
.orders {
  display: flex;
  flex-direction: column;
  gap: 18px;
  max-width: 860px;
  margin: 0 auto;
  width: 100%;
}

.tabs {
  display: flex;
  gap: 6px;
  border-bottom: 1px solid var(--fc-line);
  flex-wrap: wrap;
}

.tabs button {
  position: relative;
  padding: 11px 4px;
  margin-right: 22px;
  border: none;
  background: none;
  font-family: inherit;
  font-size: 14px;
  font-weight: 600;
  color: var(--fc-ink-400);
  cursor: pointer;
  border-bottom: 2px solid transparent;
  display: flex;
  align-items: center;
  gap: 7px;
  transition: color .16s ease, border-color .16s ease;
}

.tabs button:hover {
  color: var(--fc-ink-700);
}

.tabs button.on {
  color: var(--fc-brand-700);
  border-bottom-color: var(--fc-brand);
}

.tabs .n {
  font-size: 11px;
  font-weight: 700;
  background: #EDF1EE;
  color: var(--fc-ink-400);
  padding: 1px 7px;
  border-radius: var(--fc-r-pill);
}

.tabs button.on .n {
  background: var(--fc-brand-50);
  color: var(--fc-brand-700);
}

.list {
  display: flex;
  flex-direction: column;
}

/* ---------- 取货码票据 ---------- */
.ticket-head {
  text-align: center;
  margin-bottom: 22px;
}

.ticket-ok {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: var(--fc-brand-50);
  color: var(--fc-brand);
  display: grid;
  place-items: center;
  margin: 0 auto 12px;
  font-size: 27px;
}

.ticket-head b {
  display: block;
  font-size: 15.5px;
  font-weight: 700;
  color: var(--fc-ink-900);
  margin-bottom: 4px;
}

.ticket-head span {
  font-size: 12.5px;
  color: var(--fc-ink-400);
}

.ticket-code {
  display: flex;
  gap: 9px;
  justify-content: center;
  margin-bottom: 10px;
}

.ticket-code i {
  width: 44px;
  height: 56px;
  border-radius: 12px;
  background: #F7FCF9;
  border: 1.5px solid var(--fc-brand-100);
  display: grid;
  place-items: center;
  font-style: normal;
  font-size: 25px;
  font-weight: 750;
  color: var(--fc-brand-700);
  font-family: ui-monospace, Menlo, Consolas, monospace;
}

.ticket-tip {
  text-align: center;
  font-size: 12px;
  color: var(--fc-ink-400);
  margin: 0 0 20px;
}

.ticket-kv {
  display: flex;
  gap: 10px;
  padding: 9px 0;
  font-size: 12.5px;
  border-bottom: 1px dashed var(--fc-line-soft);
}

.ticket-kv:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.ticket-kv .k {
  width: 60px;
  flex: none;
  color: var(--fc-ink-400);
}

.ticket-kv .v {
  color: var(--fc-ink-800);
  font-weight: 600;
  flex: 1;
}

.orders { max-width: 980px; gap: 20px; }
.page-head { display: flex; align-items: end; justify-content: space-between; gap: 16px; }
.page-head h2 { margin: 0; color: var(--fc-ink-900); font-size: 27px; letter-spacing: .03em; }
.page-head p { margin: 8px 0 0; color: var(--fc-ink-500); font-size: 14px; }
.order-count { color: var(--fc-ink-500); font-size: 13px; white-space: nowrap; }
.tabs { gap: 4px; border-color: #e5e1d5; }
.tabs button { color: var(--fc-ink-500); }
.tabs .n { background: #f0ede4; color: var(--fc-ink-500); }
.ticket-code i { background: #f7f5ec; border-color: #d8e4d5; border-radius: 9px; }
.ticket-kv .v { min-width: 0; overflow-wrap: anywhere; }
@media (max-width: 600px) {
  .page-head { align-items: start; }
  .page-head h2 { font-size: 23px; }
  .tabs { flex-wrap: nowrap; overflow-x: auto; }
  .tabs button { flex: none; margin-right: 16px; white-space: nowrap; }
  .ticket-code { gap: 5px; }
  .ticket-code i { width: min(40px, 12vw); height: 50px; }
}
</style>

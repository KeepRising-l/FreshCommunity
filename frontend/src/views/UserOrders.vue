<template>
  <div class="orders">
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
        :product-title="productName(o.productId)"
        @pay="pay"
        @cancel="cancel"
        @review="openReview"
        @show-code="showCode"
      />
    </div>

    <!-- 评价弹窗 -->
    <el-dialog v-model="reviewVisible" title="评价订单" width="440px">
      <el-form label-width="80px">
        <el-form-item label="商品评分">
          <el-rate v-model="reviewForm.productScore" />
        </el-form-item>
        <el-form-item label="团长评分">
          <el-rate v-model="reviewForm.leaderScore" />
        </el-form-item>
        <el-form-item label="评论">
          <el-input
            v-model="reviewForm.content"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="说点什么吧（选填）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitReviewForm">提交评价</el-button>
      </template>
    </el-dialog>

    <!-- 取货码弹窗 -->
    <el-dialog v-model="codeVisible" title="取货凭证" width="400px">
      <div v-if="codeOrder" class="ticket">
        <div class="ticket-head">
          <div class="ticket-ok">
            <el-icon><CircleCheckFilled /></el-icon>
          </div>
          <b>{{ productName(codeOrder.productId) || '你的商品' }}</b>
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
import SkeletonList from '../components/SkeletonList.vue'
import EmptyState from '../components/EmptyState.vue'
import { listMyOrders, payOrder, cancelOrder } from '../api/order'
import { listProducts } from '../api/product'
import { submitReview } from '../api/review'
import { ORDER_STATUS_TABS } from '../constants/status'
import { formatPrice, formatDateTime, splitCode } from '../utils/format'

const router = useRouter()

const orders = ref([])
const productMap = ref({})
const loading = ref(true)
const status = ref('')

const reviewVisible = ref(false)
const submitting = ref(false)
const reviewForm = ref({ orderId: null, productScore: 0, leaderScore: 0, content: '' })

const codeVisible = ref(false)
const codeOrder = ref(null)

/** 订单接口不返回商品名，用商品列表做一次 productId → name 映射 */
function productName(productId) {
  return productMap.value[productId] || ''
}

const tabs = computed(() =>
  ORDER_STATUS_TABS.map((t) => ({
    ...t,
    count: t.value ? orders.value.filter((o) => o.status === t.value).length : orders.value.length
  }))
)

const visible = computed(() =>
  status.value ? orders.value.filter((o) => o.status === status.value) : orders.value
)

async function load() {
  loading.value = true
  try {
    const [orderRes, productRes] = await Promise.all([listMyOrders(), listProducts()])
    orders.value = orderRes.data || []
    const map = {}
    for (const p of productRes.data || []) {
      map[p.id] = p.name
    }
    productMap.value = map
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
  reviewForm.value = { orderId: order.id, productScore: 0, leaderScore: 0, content: '' }
  reviewVisible.value = true
}

async function submitReviewForm() {
  const f = reviewForm.value
  if (!f.productScore || !f.leaderScore) {
    ElMessage.warning('商品评分与团长评分均为必填')
    return
  }
  submitting.value = true
  try {
    await submitReview({
      orderId: f.orderId,
      productScore: f.productScore,
      leaderScore: f.leaderScore,
      content: f.content
    })
    ElMessage.success('评价成功')
    reviewVisible.value = false
    load()
  } catch (e) {
    // 错误提示已由请求拦截器统一处理
  } finally {
    submitting.value = false
  }
}

function showCode(order) {
  codeOrder.value = order
  codeVisible.value = true
}

onMounted(load)
</script>

<style scoped>
.orders {
  display: flex;
  flex-direction: column;
  gap: 18px;
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
</style>

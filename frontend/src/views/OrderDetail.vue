<template>
  <div class="odetail">
    <!-- 面包屑 -->
    <nav class="crumbs">
      <router-link to="/user/orders">我的订单</router-link>
      <el-icon><ArrowRight /></el-icon>
      <span>订单详情</span>
    </nav>

    <SkeletonList v-if="loading" :rows="2" variant="card" />

    <EmptyState
      v-else-if="!order"
      icon="🧾"
      title="订单不存在"
      description="它可能不属于当前账号，或已被删除。"
      action-text="返回我的订单"
      @action="router.push('/user/orders')"
    />

    <template v-else>
      <!-- 订单卡：状态、金额、操作与取货码（与列表页同组件，行为一致） -->
      <OrderCard
        :order="order"
        :product-title="order.productName || ''"
        :pickup-point-name="order.pickupPointName || ''"
        @pay="pay"
        @cancel="cancel"
        @review="reviewVisible = true"
        @show-code="codeVisible = true"
      />

      <div class="sections">
        <!-- 商品信息 -->
        <section class="fc-card section">
          <h3 class="fc-section-title">商品信息</h3>
          <div class="product-row">
            <div class="thumb" :class="visual.gradient">{{ visual.emoji }}</div>
            <div class="product-info">
              <b>{{ order.productName || '商品已删除' }}</b>
              <span class="fc-muted">数量 × {{ order.quantity }}　单价 {{ formatPrice(unitPrice) }}</span>
            </div>
            <b class="fc-price amount"><i>¥</i>{{ formatMoney(order.amount) }}</b>
          </div>
        </section>

        <!-- 团购与提货 -->
        <section class="fc-card section">
          <h3 class="fc-section-title">团购与提货点</h3>
          <div class="kv">
            <span class="k">所属团购</span>
            <span class="v link" @click="router.push('/user/groups/' + order.groupBuyId)">
              {{ order.groupBuyTitle || '团购已删除' }}
              <el-icon><ArrowRight /></el-icon>
            </span>
          </div>
          <div class="kv">
            <span class="k">团长</span>
            <span class="v">{{ order.leaderName || '—' }}</span>
          </div>
          <div class="kv">
            <span class="k">提货点</span>
            <span class="v">
              {{ order.pickupPointName || '—' }}
              <em v-if="order.pickupPointAddress" class="addr">{{ order.pickupPointAddress }}</em>
            </span>
          </div>
        </section>

        <!-- 交易信息 -->
        <section class="fc-card section">
          <h3 class="fc-section-title">交易信息</h3>
          <div class="kv">
            <span class="k">订单号</span>
            <span class="v mono">{{ order.orderNo }}</span>
          </div>
          <div class="kv">
            <span class="k">下单时间</span>
            <span class="v">{{ formatDateTime(order.createdAt) || '—' }}</span>
          </div>
          <div class="kv">
            <span class="k">支付时间</span>
            <span class="v">{{ formatDateTime(order.payTime) || '尚未支付' }}</span>
          </div>
          <div class="kv">
            <span class="k">取货码</span>
            <span class="v mono">{{ order.pickupCode || '成团后生成' }}</span>
          </div>
        </section>
      </div>

      <!-- 评价弹窗 / 取货码弹窗 -->
      <ReviewDialog v-model:visible="reviewVisible" :order="order" @submitted="load" />

      <el-dialog v-model="codeVisible" title="取货凭证" width="400px">
        <div class="ticket">
          <div class="ticket-head">
            <div class="ticket-ok">
              <el-icon><CircleCheckFilled /></el-icon>
            </div>
            <b>{{ order.productName || '你的商品' }}</b>
            <span>数量 × {{ order.quantity }}</span>
          </div>
          <div class="ticket-code">
            <i v-for="(c, i) in splitCode(order.pickupCode)" :key="i">{{ c }}</i>
          </div>
          <p class="ticket-tip">到提货点向团长出示此码，或让其输入核销</p>
          <div class="ticket-kv">
            <span class="k">订单号</span>
            <span class="v">{{ order.orderNo }}</span>
          </div>
          <div class="ticket-kv">
            <span class="k">金　额</span>
            <span class="v">{{ formatPrice(order.amount) }}</span>
          </div>
        </div>
        <template #footer>
          <el-button type="primary" @click="codeVisible = false">我知道了</el-button>
        </template>
      </el-dialog>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowRight, CircleCheckFilled } from '@element-plus/icons-vue'
import OrderCard from '../components/OrderCard.vue'
import ReviewDialog from '../components/ReviewDialog.vue'
import EmptyState from '../components/EmptyState.vue'
import SkeletonList from '../components/SkeletonList.vue'
import { getOrder, payOrder, cancelOrder } from '../api/order'
import { productVisual } from '../utils/visual'
import { formatMoney, formatPrice, formatDateTime, splitCode } from '../utils/format'

const route = useRoute()
const router = useRouter()

const order = ref(null)
const loading = ref(true)
const reviewVisible = ref(false)
const codeVisible = ref(false)

const visual = computed(() => productVisual(order.value?.productName || ''))
const unitPrice = computed(() => {
  const o = order.value
  if (!o || !o.quantity) return 0
  return Number(o.amount || 0) / o.quantity
})

async function load() {
  loading.value = true
  try {
    const res = await getOrder(route.params.id)
    order.value = res.data || null
  } catch (e) {
    order.value = null
  } finally {
    loading.value = false
  }
}

async function pay() {
  await payOrder(order.value.id)
  ElMessage.success('支付成功')
  load()
}

async function cancel() {
  await cancelOrder(order.value.id)
  ElMessage.success('取消成功')
  load()
}

onMounted(load)
</script>

<style scoped>
.odetail {
  max-width: 760px;
  margin: 0 auto;
}

.crumbs {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 13px;
  color: var(--fc-ink-400);
  margin-bottom: 16px;
}

.crumbs a {
  color: var(--fc-ink-400);
  text-decoration: none;
}

.crumbs a:hover {
  color: var(--fc-brand-700);
}

.crumbs span {
  color: var(--fc-ink-800);
  font-weight: 600;
}

.sections {
  margin-top: 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.section {
  padding: 18px 22px;
}

.product-row {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 12px;
}

.thumb {
  width: 56px;
  height: 56px;
  border-radius: 13px;
  flex: none;
  display: grid;
  place-items: center;
  font-size: 26px;
}

.product-info {
  flex: 1;
  min-width: 0;
}

.product-info b {
  display: block;
  font-size: 14.5px;
  font-weight: 650;
  color: var(--fc-ink-900);
  margin-bottom: 4px;
}

.amount {
  font-size: 17px;
}

.kv {
  display: flex;
  gap: 14px;
  padding: 10px 0;
  border-bottom: 1px dashed var(--fc-line-soft);
  font-size: 13.5px;
}

.kv:last-child {
  border-bottom: none;
  padding-bottom: 2px;
}

.kv .k {
  width: 72px;
  flex: none;
  color: var(--fc-ink-400);
}

.kv .v {
  flex: 1;
  color: var(--fc-ink-800);
  font-weight: 600;
  display: flex;
  align-items: baseline;
  gap: 8px;
  flex-wrap: wrap;
}

.kv .v.link {
  cursor: pointer;
  color: var(--fc-brand-700);
}

.kv .v.link:hover {
  text-decoration: underline;
}

.kv .v.link .el-icon {
  font-size: 12px;
}

.kv .addr {
  font-style: normal;
  font-weight: 400;
  font-size: 12.5px;
  color: var(--fc-ink-400);
}

.mono {
  font-family: ui-monospace, Menlo, Consolas, monospace;
}

/* ---------- 取货码票据（与列表页样式一致） ---------- */
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

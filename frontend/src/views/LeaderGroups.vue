<template>
  <div class="leader-groups">
    <header class="page-head">
      <div>
        <h2>我的团购</h2>
        <p class="sub">概览统计你名下全部团购；成交额不含待支付、已取消、已退款订单</p>
      </div>
      <el-button type="primary" @click="openDialog()">发起团购</el-button>
    </header>

    <section class="kpis">
      <StatCard label="进行中团购" :value="summary.ongoingGroups || 0" icon="🧺" tone="blue" />
      <StatCard label="待取货订单" :value="summary.pendingPickupOrders || 0" icon="📦" tone="orange" />
      <StatCard label="已核销订单" :value="summary.completedOrders || 0" icon="✅" tone="green" />
      <StatCard label="累计成交额" :value="'¥' + money(summary.totalGmv)" icon="💰" tone="purple" />
    </section>

    <section class="fc-card table-card">
      <div class="card-head">
        <h3>团购列表</h3>
        <span class="fc-muted">共 {{ groups.length }} 场</span>
      </div>

      <el-table
        v-loading="loading"
        :data="groups"
        empty-text="还没有发起过团购"
      >
        <el-table-column prop="title" label="标题" min-width="150" show-overflow-tooltip />
        <el-table-column prop="targetCount" label="成团人数" width="100" align="center" />
        <el-table-column prop="memberCount" label="已支付人数" width="110" align="center" />
        <el-table-column label="还差几人" width="100" align="center">
          <template #default="scope">
            <span v-if="scope.row.status === 'FAIL'" class="fc-muted">—</span>
            <span v-else-if="gapOf(scope.row) > 0" class="gap-text fc-num">
              {{ gapOf(scope.row) }} 人
            </span>
            <span v-else class="ok-text">已达标</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="scope">
            <StatusBadge kind="groupBuy" :status="scope.row.status" />
          </template>
        </el-table-column>
        <el-table-column label="核销进度" width="150">
          <template #default="scope">
            <div v-if="progressOf(scope.row)" class="progress-cell">
              <div class="progress-meta">
                <b class="fc-num">
                  {{ progressOf(scope.row).completed }}/{{ progressOf(scope.row).total }}
                </b>
                <small>已核销</small>
              </div>
              <div class="fc-progress">
                <i :style="{ width: progressOf(scope.row).percent + '%' }" />
              </div>
            </div>
            <span v-else class="fc-muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="截止时间" width="150">
          <template #default="scope">
            <span class="fc-num time">{{ formatTime(scope.row.deadline) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" align="right">
          <template #default="scope">
            <el-button size="small" @click="viewOrders(scope.row)">查看订单</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="dialogVisible" title="发起团购" width="440px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="标题">
          <el-input v-model="form.title" />
        </el-form-item>
        <el-form-item label="商品">
          <el-select v-model="form.productId" placeholder="选择商品" style="width:100%;">
            <el-option v-for="p in products" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="拼团价">
          <el-input-number v-model="form.price" :min="0.01" :precision="2" :step="1"
                           :placeholder="selectedProduct ? `默认 ¥${selectedProduct.price}` : '默认取零售价'" />
          <span v-if="selectedProduct" class="price-hint">
            零售价 ¥{{ selectedProduct.price }}，不填则按零售价开团
          </span>
        </el-form-item>
        <el-form-item label="成团人数">
          <el-input-number v-model="form.targetCount" :min="1" />
        </el-form-item>
        <el-form-item label="截止时间">
          <el-date-picker v-model="form.deadline" type="datetime" placeholder="选择截止时间" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="orderDialogVisible" title="订单列表" width="620px">
      <el-table :data="orders" empty-text="该团暂无订单">
        <el-table-column prop="orderNo" label="订单号" min-width="170" show-overflow-tooltip />
        <el-table-column prop="quantity" label="数量" width="70" align="center" />
        <el-table-column prop="amount" label="金额" width="90" align="right" />
        <el-table-column label="状态" width="100">
          <template #default="scope">
            <StatusBadge kind="order" :status="scope.row.status" />
          </template>
        </el-table-column>
        <el-table-column prop="pickupCode" label="取货码" width="100" align="center" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, reactive, computed } from 'vue'
import {
  listMyGroupBuys,
  createGroupBuy,
  listGroupBuyOrders,
  getMyGroupBuySummary
} from '../api/groupBuy'
import { listProducts } from '../api/product'
import StatCard from '../components/StatCard.vue'
import StatusBadge from '../components/StatusBadge.vue'

const groups = ref([])
const products = ref([])
const summary = ref({})
const progress = ref({})
const loading = ref(true)
const dialogVisible = ref(false)
const orderDialogVisible = ref(false)
const orders = ref([])
const form = reactive({ title: '', productId: null, price: null, targetCount: 1, deadline: '' })

const selectedProduct = computed(() => products.value.find((p) => p.id === form.productId) || null)

const money = (v) =>
  Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

/** 进行中的团还差几人；已成团/已解散不适用 */
function gapOf(row) {
  if (row.status !== 'ONGOING') return 0
  return Math.max((row.targetCount || 0) - (row.memberCount || 0), 0)
}

function formatTime(v) {
  return v ? String(v).replace('T', ' ').slice(0, 16) : '—'
}

/** 已成团的团才有核销进度：已核销 / 应取货（已核销 + 待取货） */
function progressOf(row) {
  if (row.status !== 'SUCCESS') return null
  const p = progress.value[row.id]
  if (!p || !p.total) return null
  return p
}

async function load() {
  loading.value = true
  try {
    const [groupsRes, summaryRes] = await Promise.all([
      listMyGroupBuys(),
      getMyGroupBuySummary()
    ])
    groups.value = groupsRes.data || []
    summary.value = summaryRes.data || {}
    await loadProgress()
  } finally {
    loading.value = false
  }
}

async function loadProgress() {
  const succeeded = groups.value.filter((g) => g.status === 'SUCCESS')
  if (!succeeded.length) {
    progress.value = {}
    return
  }
  const entries = await Promise.all(
    succeeded.map(async (g) => {
      const res = await listGroupBuyOrders(g.id)
      const rows = res.data || []
      const completed = rows.filter((o) => o.status === 'COMPLETED').length
      const pending = rows.filter((o) => o.status === 'SUCCESS' || o.status === 'PENDING_PICKUP').length
      const total = completed + pending
      return [g.id, { completed, total, percent: total ? Math.round((completed / total) * 100) : 0 }]
    })
  )
  progress.value = Object.fromEntries(entries)
}

async function loadProducts() {
  const res = await listProducts()
  products.value = res.data
}

function openDialog() {
  Object.assign(form, { title: '', productId: null, price: null, targetCount: 1, deadline: '' })
  dialogVisible.value = true
}

async function submit() {
  // price 留空表示按零售价开团，不发送该字段，避免后端收到 null 之外的空值
  const payload = { ...form }
  if (payload.price == null) delete payload.price
  await createGroupBuy(payload)
  dialogVisible.value = false
  load()
}

async function viewOrders(row) {
  const res = await listGroupBuyOrders(row.id)
  orders.value = res.data || []
  orderDialogVisible.value = true
}

onMounted(() => {
  load()
  loadProducts()
})
</script>

<style scoped>
.leader-groups {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.page-head h2 {
  margin: 0 0 6px;
  font-size: 20px;
  font-weight: 750;
  letter-spacing: -0.4px;
  color: var(--fc-ink-900);
}

.sub {
  margin: 0;
  font-size: 12.5px;
  color: var(--fc-ink-400);
}

.kpis {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 14px;
}

.table-card {
  padding: 18px 20px 8px;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.card-head h3 {
  margin: 0;
  font-size: 14.5px;
  font-weight: 700;
  color: var(--fc-ink-900);
}

.gap-text {
  font-weight: 700;
  color: var(--fc-accent-600);
}

.ok-text {
  font-weight: 650;
  color: var(--fc-brand-700);
}

.progress-cell {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.progress-meta {
  display: flex;
  align-items: baseline;
  gap: 5px;
  line-height: 1.2;
}

.progress-meta b {
  font-size: 13px;
  color: var(--fc-ink-800);
}

.progress-meta small {
  font-size: 10.5px;
  color: var(--fc-ink-400);
}

.progress-cell .fc-progress {
  height: 5px;
  max-width: 96px;
}

.time {
  font-size: 12.5px;
  color: var(--fc-ink-500);
}

.price-hint {
  margin-left: 8px;
  color: var(--fc-ink-400);
  font-size: 12px;
}

@media (max-width: 720px) {
  .table-card {
    padding: 14px 12px 6px;
  }
}
</style>

<template>
  <div class="admin-orders">
    <!-- 页头 -->
    <header class="page-head">
      <div>
        <h2>订单管理</h2>
        <p>平台全部订单，异常订单可在此执行退款兜底</p>
      </div>
    </header>

    <!-- 筛选 -->
    <div class="toolbar fc-card">
      <el-input
        v-model="keyword"
        placeholder="搜订单号或买家用户名…"
        clearable
        class="kw"
        @keyup.enter="search"
        @clear="search"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-select v-model="status" class="st" @change="search">
        <el-option v-for="t in ORDER_STATUS_TABS" :key="t.value" :label="t.label || '全部状态'" :value="t.value" />
      </el-select>
      <el-button @click="search">查询</el-button>
    </div>

    <!-- 表格 -->
    <div class="fc-card table-wrap" v-loading="loading">
      <el-table :data="orders" style="width: 100%">
        <el-table-column label="订单号" width="200">
          <template #default="{ row }">
            <span class="mono no">{{ row.orderNo }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="buyerName" label="买家" width="100">
          <template #default="{ row }">
            <span>{{ row.buyerName || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="商品 / 团购" min-width="200">
          <template #default="{ row }">
            <div class="pt">
              <b>{{ row.productName || '（商品已删除）' }}</b>
              <span class="fc-muted">× {{ row.quantity }} · {{ row.groupBuyTitle || '（团购已删除）' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="100">
          <template #default="{ row }">
            <span class="fc-price"><i>¥</i>{{ formatMoney(row.amount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="96">
          <template #default="{ row }">
            <StatusBadge kind="order" :status="row.status" />
          </template>
        </el-table-column>
        <el-table-column label="下单时间" width="120">
          <template #default="{ row }">
            <span class="fc-muted">{{ formatDateTime(row.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-tooltip
              :disabled="refundable(row)"
              content="终态订单（已完成/已取消/已退款）不可退款"
              placement="top"
            >
              <span>
                <el-button size="small" type="danger" :disabled="!refundable(row)" @click="refund(row)">
                  退款
                </el-button>
              </span>
            </el-tooltip>
          </template>
        </el-table-column>
        <template #empty>
              <EmptyState icon="🧾" title="没有找到订单" :description="hasFilter ? '换个筛选条件试试。' : '平台还没有订单。'" />
        </template>
      </el-table>
    </div>

    <!-- 分页 -->
    <div v-if="total > 0" class="pager">
      <el-pagination
        layout="total, prev, pager, next"
        :total="total"
        :current-page="page"
        :page-size="size"
        background
        @current-change="changePage"
      />
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import StatusBadge from '../components/StatusBadge.vue'
import EmptyState from '../components/EmptyState.vue'
import { listAdminOrders, refundOrder } from '../api/order'
import { ORDER_STATUS_TABS } from '../constants/status'
import { formatMoney, formatDateTime } from '../utils/format'

/** 终态订单不可退款（与后端 OrderService.refundOrderByAdmin 的守卫一致） */
const REFUNDABLE = ['PENDING_PAYMENT', 'PENDING_GROUP', 'SUCCESS', 'PENDING_PICKUP']

const orders = ref([])
const total = ref(0)
const loading = ref(true)
const page = ref(1)
const size = 10

const keyword = ref('')
const status = ref('')
const appliedKeyword = ref('')
const appliedStatus = ref('')
const hasFilter = computed(() => !!appliedKeyword.value || !!appliedStatus.value)

function refundable(row) {
  return REFUNDABLE.includes(row.status)
}

async function load() {
  loading.value = true
  try {
    const res = await listAdminOrders({
      keyword: appliedKeyword.value || undefined,
      status: appliedStatus.value || undefined,
      page: page.value,
      size
    })
    orders.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (e) {
    orders.value = []
  } finally {
    loading.value = false
  }
}

function search() {
  appliedKeyword.value = keyword.value.trim()
  appliedStatus.value = status.value
  page.value = 1
  load()
}

function changePage(p) {
  page.value = p
  load()
}

async function refund(row) {
  const ok = await ElMessageBox.confirm(
    `确定将订单 ${row.orderNo}（${row.buyerName || '买家'}，¥${formatMoney(row.amount)}）标记为已退款吗？库存将回补，买家会收到站内通知。`,
    '管理员退款',
    { type: 'warning', confirmButtonText: '确认退款', cancelButtonText: '取消' }
  ).catch(() => false)
  if (!ok) return
  await refundOrder(row.id)
  ElMessage.success('已退款')
  load()
}

onMounted(load)
</script>

<style scoped>
.admin-orders {
  max-width: 1120px;
  margin: 0 auto;
}

.page-head {
  margin-bottom: 16px;
}

.page-head h2 {
  margin: 0 0 4px;
  font-size: 21px;
  font-weight: 700;
  color: var(--fc-ink-900);
}

.page-head p {
  margin: 0;
  font-size: 13px;
  color: var(--fc-ink-400);
}

.toolbar {
  display: flex;
  gap: 10px;
  padding: 13px 16px;
  margin-bottom: 14px;
}

.toolbar .kw {
  width: 280px;
}

.toolbar .st {
  width: 130px;
}

.table-wrap {
  padding: 6px 10px 10px;
  min-height: 200px;
}

.mono {
  font-family: ui-monospace, Menlo, Consolas, monospace;
  font-size: 12px;
}

.pt b {
  display: block;
  font-size: 13.5px;
  color: var(--fc-ink-800);
}

.pt span {
  font-size: 11.5px;
}

.pager {
  display: flex;
  justify-content: center;
  margin-top: 18px;
}
</style>

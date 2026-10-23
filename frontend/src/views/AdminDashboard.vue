<template>
  <div class="dash">
    <header class="page-head">
      <div>
        <h2>数据看板</h2>
        <p class="sub">成交额 = 今日创建、且不处于「待支付 / 已取消 / 已退款」的订单金额之和</p>
      </div>
      <span v-if="updatedAt" class="stamp">更新于 {{ updatedAt }}</span>
    </header>

    <section v-if="loading" class="kpis">
      <SkeletonList :rows="1" variant="row" />
    </section>
    <section v-else class="kpis">
      <StatCard
        v-for="k in kpis"
        :key="k.label"
        :label="k.label"
        :value="k.value"
        :icon="k.icon"
        :tone="k.tone"
      />
    </section>

    <section class="panels">
      <!-- 近 7 日趋势 -->
      <article class="card trend">
        <div class="card-head">
          <h3>近 7 日趋势</h3>
          <div class="legend">
            <span><i class="swatch swatch-gmv" />成交额</span>
            <span><i class="swatch swatch-orders" />订单数</span>
          </div>
        </div>

        <svg
          class="chart"
          :viewBox="`0 0 ${CHART_W} ${CHART_H}`"
          role="img"
          aria-label="近 7 日成交额与订单数趋势"
        >
          <defs>
            <linearGradient id="gmvFill" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stop-color="#1E9E5A" stop-opacity=".22" />
              <stop offset="100%" stop-color="#1E9E5A" stop-opacity="0" />
            </linearGradient>
          </defs>

          <line
            v-for="g in gridLines"
            :key="g"
            :x1="PAD"
            :x2="CHART_W - PAD"
            :y1="g"
            :y2="g"
            class="grid-line"
          />

          <path :d="areaPath" fill="url(#gmvFill)" />
          <path :d="orderLinePath" class="order-line" />
          <path :d="linePath" class="gmv-line" />

          <g v-for="p in points" :key="p.date">
            <circle :cx="p.x" :cy="p.y" r="4" class="point" />
            <circle :cx="p.x" :cy="p.orderY" r="2.5" class="point-order" />
          </g>
        </svg>

        <div class="axis">
          <div v-for="p in points" :key="p.date" class="axis-item">
            <span class="axis-date">{{ p.date.slice(5) }}</span>
            <b class="axis-gmv fc-num">¥{{ money(p.gmv) }}</b>
            <small>{{ p.orders }} 单</small>
          </div>
        </div>
      </article>

      <!-- 订单状态分布 -->
      <article class="card dist">
        <div class="card-head">
          <h3>订单状态分布</h3>
          <span class="total">累计 {{ totalOrderCount }} 单</span>
        </div>

        <EmptyState
          v-if="!distribution.length"
          icon="📊"
          title="还没有订单"
          description="产生第一笔订单后，这里会按状态展示占比。"
        />

        <div v-else class="bars">
          <div v-for="item in distribution" :key="item.status" class="bar-row">
            <div class="bar-meta">
              <StatusBadge kind="order" :status="item.status" :dot="false" />
              <span class="bar-count fc-num">{{ item.count }}</span>
            </div>
            <div class="bar-track">
              <div
                class="bar-fill"
                :class="'tone-' + item.tone"
                :style="{ width: item.percent + '%' }"
              />
            </div>
          </div>
        </div>
      </article>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import StatCard from '../components/StatCard.vue'
import StatusBadge from '../components/StatusBadge.vue'
import EmptyState from '../components/EmptyState.vue'
import SkeletonList from '../components/SkeletonList.vue'
import { getDashboard } from '../api/admin'
import { ORDER_STATUS, orderStatus } from '../constants/status'

/* ---------- 图表几何：固定 viewBox，宽度由容器决定 ---------- */
const CHART_W = 720
const CHART_H = 180
const PAD = 16
const gridLines = [PAD, CHART_H / 2, CHART_H - PAD]

const loading = ref(true)
const data = ref({})
const updatedAt = ref('')

const money = (v) =>
  Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

const kpis = computed(() => {
  const d = data.value
  return [
    { label: '今日订单数', value: d.todayOrders ?? 0, icon: '🧾', tone: 'green' },
    { label: '今日成团数', value: d.todaySuccessGroups ?? 0, icon: '📦', tone: 'orange' },
    { label: '今日成交额', value: '¥' + money(d.todayGmv), icon: '💰', tone: 'blue' },
    { label: '注册用户', value: d.totalUsers ?? 0, icon: '👥', tone: 'purple' },
    { label: '进行中团购', value: d.ongoingGroupBuys ?? 0, icon: '🧺', tone: 'gray' }
  ]
})

const trend = computed(() => data.value.trend7d || [])

const maxGmv = computed(() => {
  const max = Math.max(...trend.value.map((p) => Number(p.gmv) || 0))
  return max > 0 ? max : 1
})

const maxOrders = computed(() => {
  const max = Math.max(...trend.value.map((p) => Number(p.orders) || 0))
  return max > 0 ? max : 1
})

const points = computed(() => {
  const n = trend.value.length
  return trend.value.map((p, i) => {
    const x = n <= 1 ? CHART_W / 2 : PAD + (i * (CHART_W - PAD * 2)) / (n - 1)
    const gmvY = CHART_H - PAD - ((Number(p.gmv) || 0) / maxGmv.value) * (CHART_H - PAD * 2)
    // 订单数用下方一条浅色折线表示，高度压缩到下半区，避免与成交额线混淆
    const orderY =
      CHART_H - PAD - ((Number(p.orders) || 0) / maxOrders.value) * (CHART_H - PAD * 2) * 0.55
    return { ...p, x, y: gmvY, orderY }
  })
})

const linePath = computed(() => {
  if (!points.value.length) return ''
  return points.value
    .map((p, i) => `${i === 0 ? 'M' : 'L'}${p.x.toFixed(1)},${p.y.toFixed(1)}`)
    .join(' ')
})

const orderLinePath = computed(() => {
  if (!points.value.length) return ''
  return points.value
    .map((p, i) => `${i === 0 ? 'M' : 'L'}${p.x.toFixed(1)},${p.orderY.toFixed(1)}`)
    .join(' ')
})

const areaPath = computed(() => {
  if (!points.value.length) return ''
  const base = CHART_H - PAD
  const first = points.value[0]
  const last = points.value[points.value.length - 1]
  return `${linePath.value} L${last.x.toFixed(1)},${base} L${first.x.toFixed(1)},${base} Z`
})

const distribution = computed(() => {
  const rows = data.value.orderStatusDistribution || []
  const counts = new Map(rows.map((r) => [r.status, Number(r.count) || 0]))
  const total = rows.reduce((sum, r) => sum + (Number(r.count) || 0), 0)

  const known = Object.keys(ORDER_STATUS)
  const extra = rows.map((r) => r.status).filter((s) => !ORDER_STATUS[s])
  return [...known, ...extra]
    .filter((s) => counts.get(s) > 0)
    .map((s) => {
      const meta = orderStatus(s)
      const count = counts.get(s)
      return {
        status: s,
        tone: meta.tone,
        count,
        percent: total ? Math.round((count / total) * 1000) / 10 : 0
      }
    })
})

const totalOrderCount = computed(() =>
  (data.value.orderStatusDistribution || []).reduce((sum, r) => sum + (Number(r.count) || 0), 0)
)

async function load() {
  loading.value = true
  try {
    const res = await getDashboard()
    data.value = res.data || {}
    updatedAt.value = new Date().toLocaleTimeString('zh-CN', {
      hour: '2-digit',
      minute: '2-digit'
    })
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.dash {
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

.stamp {
  font-size: 11.5px;
  color: var(--fc-ink-400);
  background: var(--fc-card);
  border: 1px solid var(--fc-line);
  border-radius: var(--fc-r-pill);
  padding: 4px 10px;
}

.kpis {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 14px;
}

.panels {
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(280px, 1fr);
  gap: 16px;
  align-items: start;
}

.card {
  background: var(--fc-card);
  border: 1px solid var(--fc-line);
  border-radius: var(--fc-r-lg);
  box-shadow: var(--fc-sh-1);
  padding: 18px 20px 20px;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.card-head h3 {
  margin: 0;
  font-size: 14.5px;
  font-weight: 700;
  color: var(--fc-ink-900);
}

.legend {
  display: flex;
  gap: 14px;
  font-size: 11.5px;
  color: var(--fc-ink-400);
}

.legend span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.swatch {
  width: 14px;
  height: 3px;
  border-radius: 2px;
}

.swatch-gmv {
  background: var(--fc-brand);
}

.swatch-orders {
  background: var(--fc-accent);
}

.chart {
  display: block;
  width: 100%;
  height: auto;
  overflow: visible;
}

.grid-line {
  stroke: var(--fc-line-soft);
  stroke-width: 1;
}

.gmv-line {
  fill: none;
  stroke: var(--fc-brand);
  stroke-width: 2.5;
  stroke-linejoin: round;
  stroke-linecap: round;
}

.order-line {
  fill: none;
  stroke: var(--fc-accent);
  stroke-width: 1.5;
  stroke-dasharray: 5 5;
  stroke-linejoin: round;
  opacity: .75;
}

.point {
  fill: #fff;
  stroke: var(--fc-brand);
  stroke-width: 2.5;
}

.point-order {
  fill: var(--fc-accent);
}

.axis {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 4px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed var(--fc-line);
}

.axis-item {
  min-width: 0;
  text-align: center;
  line-height: 1.5;
}

.axis-date {
  display: block;
  font-size: 11px;
  color: var(--fc-ink-400);
  white-space: nowrap;
}

.axis-gmv {
  display: block;
  font-size: 11.5px;
  font-weight: 700;
  color: var(--fc-ink-800);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.axis-item small {
  display: block;
  font-size: 10.5px;
  color: var(--fc-ink-400);
  white-space: nowrap;
}

.total {
  font-size: 11.5px;
  color: var(--fc-ink-400);
}

.bars {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.bar-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 5px;
}

.bar-count {
  font-size: 12.5px;
  font-weight: 700;
  color: var(--fc-ink-700);
}

.bar-track {
  height: 8px;
  border-radius: var(--fc-r-pill);
  background: var(--fc-line-soft);
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  min-width: 4px;
  border-radius: var(--fc-r-pill);
  transition: width 0.4s ease;
}

.tone-green { background: var(--fc-brand); }
.tone-orange { background: var(--fc-accent); }
.tone-blue { background: var(--fc-info); }
.tone-yellow { background: var(--fc-warning); }
.tone-red { background: var(--fc-danger); }
.tone-purple { background: var(--fc-purple); }
.tone-gray { background: var(--fc-ink-300); }

@media (max-width: 1080px) {
  .panels {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 560px) {
  .axis-item small,
  .axis-gmv {
    font-size: 10px;
  }
}
</style>

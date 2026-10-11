<template>
  <div class="plaza">
    <!-- 引导区 -->
    <section class="hero">
      <div class="hero-text">
        <span class="hero-tag">🎉 今日新鲜直达 · 满员即享拼团价</span>
        <h2>和邻居一起拼，<em>省下的都是新鲜的</em></h2>
        <p>楼下提货点自提免运费，未成团自动全额退款</p>
        <div class="hero-btns">
          <el-button type="primary" size="large" @click="filter = 'urgent'">看看即将截止的团</el-button>
          <el-button size="large" @click="goOrders">我的订单</el-button>
        </div>
      </div>
      <div class="hero-stats">
        <div class="hs">
          <b>{{ all.length }}</b>
          <span>进行中</span>
        </div>
        <div class="hs">
          <b>{{ urgentCount }}</b>
          <span>即将截止</span>
        </div>
        <div class="hs">
          <b>{{ almostCount }}</b>
          <span>马上成团</span>
        </div>
      </div>
    </section>

    <!-- 筛选 -->
    <div class="filters">
      <span
        v-for="f in FILTERS"
        :key="f.value"
        class="chip"
        :class="{ on: filter === f.value }"
        @click="filter = f.value"
      >
        {{ f.label }}
        <b v-if="f.count">{{ f.count }}</b>
      </span>
      <span class="spacer" />
      <span class="sort">按截止时间排序</span>
    </div>

    <!-- 列表 -->
    <SkeletonList v-if="loading" :rows="3" variant="card" />

    <EmptyState
      v-else-if="visible.length === 0"
      icon="🧺"
      :title="all.length === 0 ? '暂时没有进行中的团购' : '这个筛选下没有团购'"
      :description="all.length === 0 ? '团长开团后会出现在这里，稍后再来看看。' : '换个筛选条件试试。'"
      :action-text="all.length === 0 ? '' : '查看全部'"
      @action="filter = 'all'"
    />

    <div v-else class="grid">
      <GroupBuyCard
        v-for="g in visible"
        :key="g.id"
        :group="g"
        :action-text="'立即参团' + (remainingMembers(g.memberCount, g.targetCount) > 0 ? ' · 还差 ' + remainingMembers(g.memberCount, g.targetCount) + ' 人' : '')"
        @action="openJoin(g)"
        @click="goDetail(g)"
      />
    </div>

    <!-- 参团下单 -->
    <el-dialog v-model="dialogVisible" title="参加团购" width="420px">
      <div v-if="current" class="join-body">
        <div class="join-prod">
          <div class="join-thumb" :class="current.imageUrl ? '' : joinVisual.gradient">
            <img v-if="current.imageUrl" :src="current.imageUrl" :alt="current.productName" />
            <span v-else>{{ joinVisual.emoji }}</span>
          </div>
          <div class="join-info">
            <h4>{{ current.title || current.productName }}</h4>
            <div class="fc-price" style="font-size: 19px">
              <i>¥</i>{{ formatMoney(current.price) }}
            </div>
          </div>
        </div>

        <div class="join-row">
          <span class="join-label">购买数量</span>
          <el-input-number v-model="quantity" :min="1" :max="99" />
        </div>

        <div class="join-row">
          <span class="join-label">提货点</span>
          <span class="join-value">{{ current.pickupPointName || '—' }}</span>
        </div>

        <div class="join-note">
          🛡️ 支付后订单进入「待成团」，截止前未达 {{ current.targetCount }} 人将自动解散并全额退款。
        </div>
      </div>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">确认下单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import GroupBuyCard from '../components/GroupBuyCard.vue'
import SkeletonList from '../components/SkeletonList.vue'
import EmptyState from '../components/EmptyState.vue'
import { listGroupBuys } from '../api/groupBuy'
import { createOrder } from '../api/order'
import { useNow } from '../composables/useNow'
import { productVisual } from '../utils/visual'
import { formatMoney, remaining, progressPercent, remainingMembers } from '../utils/format'

const router = useRouter()
const now = useNow()

const all = ref([])
const loading = ref(true)
const filter = ref('all')

const dialogVisible = ref(false)
const submitting = ref(false)
const quantity = ref(1)
const current = ref(null)

/** 即将截止：2 小时内到期 */
function isUrgent(g) {
  const r = remaining(g.deadline, now.value)
  return !r.expired && r.urgent
}

/** 马上成团：进度已达 60% */
function isAlmost(g) {
  return progressPercent(g.memberCount, g.targetCount) >= 60
}

const urgentCount = computed(() => all.value.filter(isUrgent).length)
const almostCount = computed(() => all.value.filter(isAlmost).length)

const FILTERS = computed(() => [
  { value: 'all', label: '全部', count: all.value.length },
  { value: 'urgent', label: '⏰ 即将截止', count: urgentCount.value },
  { value: 'almost', label: '🔥 马上成团', count: almostCount.value }
])

const visible = computed(() => {
  if (filter.value === 'urgent') return all.value.filter(isUrgent)
  if (filter.value === 'almost') return all.value.filter(isAlmost)
  return all.value
})

const joinVisual = computed(() => productVisual(current.value?.productName || current.value?.title || ''))

async function load() {
  loading.value = true
  try {
    const res = await listGroupBuys()
    all.value = res.data || []
  } catch (e) {
    all.value = []
  } finally {
    loading.value = false
  }
}

function openJoin(g) {
  current.value = g
  quantity.value = 1
  dialogVisible.value = true
}

function goDetail(g) {
  router.push('/user/groups/' + g.id)
}

function goOrders() {
  router.push('/user/orders')
}

async function submit() {
  if (!current.value) return
  submitting.value = true
  try {
    await createOrder(current.value.id, quantity.value)
    ElMessage.success('下单成功，请去我的订单完成支付')
    dialogVisible.value = false
    router.push('/user/orders')
  } catch (e) {
    // 错误提示已由请求拦截器统一处理
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.plaza {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* ---------- 引导区 ---------- */
.hero {
  position: relative;
  overflow: hidden;
  border-radius: var(--fc-r-xl);
  padding: 30px 34px;
  color: #fff;
  background: linear-gradient(120deg, #1E9E5A 0%, #178A4E 55%, #0F6B3C 100%);
  display: flex;
  align-items: center;
  gap: 28px;
}

.hero::before {
  content: "";
  position: absolute;
  width: 440px;
  height: 440px;
  border-radius: 50%;
  right: -120px;
  top: -180px;
  background: radial-gradient(circle, rgba(255, 255, 255, .14), transparent 68%);
}

.hero::after {
  content: "";
  position: absolute;
  width: 260px;
  height: 260px;
  border-radius: 50%;
  right: 150px;
  bottom: -170px;
  background: radial-gradient(circle, rgba(255, 122, 26, .34), transparent 66%);
}

.hero-text {
  position: relative;
  z-index: 2;
  flex: 1;
  min-width: 0;
}

.hero-tag {
  display: inline-flex;
  align-items: center;
  font-size: 12px;
  font-weight: 600;
  background: rgba(255, 255, 255, .17);
  border: 1px solid rgba(255, 255, 255, .26);
  padding: 5px 13px;
  border-radius: var(--fc-r-pill);
  margin-bottom: 15px;
}

.hero h2 {
  margin: 0 0 9px;
  font-size: 27px;
  font-weight: 750;
  letter-spacing: -.5px;
  line-height: 1.3;
}

.hero h2 em {
  font-style: normal;
  color: #FFC48A;
}

.hero p {
  margin: 0 0 20px;
  font-size: 13.5px;
  opacity: .84;
}

.hero-btns {
  display: flex;
  gap: 10px;
}

.hero-btns :deep(.el-button) {
  border: none;
}

.hero-btns :deep(.el-button:not(.el-button--primary)) {
  background: rgba(255, 255, 255, .14);
  color: #fff;
  border: 1px solid rgba(255, 255, 255, .34);
}

.hero-btns :deep(.el-button:not(.el-button--primary):hover) {
  background: rgba(255, 255, 255, .24);
}

.hero-stats {
  position: relative;
  z-index: 2;
  display: flex;
  gap: 10px;
  flex: none;
}

.hs {
  background: rgba(255, 255, 255, .13);
  border: 1px solid rgba(255, 255, 255, .22);
  border-radius: var(--fc-r);
  padding: 14px 18px;
  text-align: center;
  min-width: 92px;
}

.hs b {
  display: block;
  font-size: 23px;
  font-weight: 750;
  letter-spacing: -.5px;
  font-variant-numeric: tabular-nums;
}

.hs span {
  font-size: 11.5px;
  opacity: .8;
}

/* ---------- 筛选 ---------- */
.filters {
  display: flex;
  align-items: center;
  gap: 9px;
  flex-wrap: wrap;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 15px;
  border-radius: var(--fc-r-pill);
  background: #fff;
  border: 1px solid var(--fc-line);
  font-size: 13px;
  color: var(--fc-ink-500);
  font-weight: 500;
  cursor: pointer;
  transition: all .15s ease;
  user-select: none;
}

.chip:hover {
  border-color: var(--fc-brand-100);
  color: var(--fc-brand-700);
  background: var(--fc-brand-50);
}

.chip.on {
  background: var(--fc-brand);
  border-color: var(--fc-brand);
  color: #fff;
  font-weight: 600;
  box-shadow: 0 2px 8px rgba(30, 158, 90, .26);
}

.chip b {
  font-size: 11px;
  font-weight: 700;
  opacity: .7;
}

.spacer {
  flex: 1;
}

.sort {
  font-size: 12.5px;
  color: var(--fc-ink-400);
  border: 1px solid var(--fc-line);
  background: #fff;
  border-radius: var(--fc-r-pill);
  height: 32px;
  display: inline-flex;
  align-items: center;
  padding: 0 13px;
}

/* ---------- 网格 ---------- */
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
}

/* ---------- 参团弹窗 ---------- */
.join-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.join-prod {
  display: flex;
  gap: 13px;
  padding: 13px;
  background: var(--fc-card-2);
  border: 1px solid var(--fc-line-soft);
  border-radius: var(--fc-r);
}

.join-thumb {
  width: 62px;
  height: 62px;
  border-radius: 12px;
  flex: none;
  display: grid;
  place-items: center;
  font-size: 28px;
  overflow: hidden;
}

.join-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.join-info h4 {
  margin: 0 0 6px;
  font-size: 14px;
  font-weight: 650;
  color: var(--fc-ink-900);
}

.join-row {
  display: flex;
  align-items: center;
}

.join-label {
  font-size: 13.5px;
  color: var(--fc-ink-500);
  flex: none;
}

.join-value {
  margin-left: auto;
  font-size: 13.5px;
  color: var(--fc-ink-800);
  font-weight: 600;
}

.join-note {
  padding: 11px 13px;
  border-radius: var(--fc-r);
  background: var(--fc-brand-50);
  border: 1px solid var(--fc-brand-100);
  font-size: 11.5px;
  color: var(--fc-brand-700);
  line-height: 1.7;
}

@media (max-width: 1100px) {
  .hero-stats {
    display: none;
  }
}

@media (max-width: 700px) {
  .hero {
    padding: 22px 20px;
  }

  .hero h2 {
    font-size: 21px;
  }
}
</style>

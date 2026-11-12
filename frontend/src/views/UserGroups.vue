<template>
  <div class="plaza">
    <section class="plaza-intro">
      <div>
        <span class="intro-label">邻里好物 · 社区自提</span>
        <h2>和邻居一起，团点新鲜</h2>
        <p>挑选喜欢的食材，成团后到社区提货点自提。</p>
      </div>
      <button type="button" class="order-link" @click="goOrders">查看我的订单 <span aria-hidden="true">→</span></button>
    </section>

    <!-- 搜索与分类控制栏 -->
    <div class="plaza-controls">
      <div class="search-box">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索团购商品或标题…"
          clearable
          :prefix-icon="Search"
          style="width: 260px"
        />
      </div>
      <div v-if="categories.length > 1" class="cat-tabs">
        <button
          v-for="cat in categories"
          :key="cat"
          type="button"
          class="cat-chip"
          :class="{ on: selectedCategory === cat }"
          :aria-pressed="selectedCategory === cat"
          @click="selectedCategory = cat"
        >
          {{ cat }}
        </button>
      </div>
    </div>

    <!-- 筛选 -->
    <div class="filters">
      <button
        v-for="f in FILTERS"
        :key="f.value"
        type="button"
        class="chip"
        :class="{ on: filter === f.value }"
        :aria-pressed="filter === f.value"
        @click="filter = f.value"
      >
        {{ f.label }}
        <b v-if="f.count">{{ f.count }}</b>
      </button>
      <span class="spacer" />
      <span class="sort-group">
        <button
          v-for="s in SORTS"
          :key="s.value"
          class="sort-btn"
          :class="{ on: sort === s.value }"
          @click="sort = s.value"
        >
          {{ s.label }}
        </button>
      </span>
    </div>

    <!-- 列表 -->
    <SkeletonList v-if="loading" :rows="3" variant="card" />

    <EmptyState
      v-else-if="visible.length === 0"
      icon="🧺"
      :title="all.length === 0 ? '暂时没有进行中的团购' : '未找到匹配的团购'"
      :description="all.length === 0 ? '团长开团后会出现在这里，稍后再来看看。' : '换个关键词或分类条件试试。'"
      :action-text="all.length === 0 ? '' : '重置筛选'"
      @action="resetFilters"
    />

    <template v-else>
      <div class="featured-groups">
        <GroupBuyCard
          :group="visible[0]"
          layout="feature"
          :action-text="'立即参团' + (remainingMembers(visible[0].memberCount, visible[0].targetCount) > 0 ? ' · 还差 ' + remainingMembers(visible[0].memberCount, visible[0].targetCount) + ' 人' : '')"
          @action="openJoin(visible[0])"
          @click="goDetail(visible[0])"
        />
        <div v-if="visible.length > 1" class="featured-list">
          <GroupBuyCard
            v-for="g in visible.slice(1, 4)"
            :key="g.id"
            :group="g"
            layout="row"
            :action-text="'立即参团'"
            @action="openJoin(g)"
            @click="goDetail(g)"
          />
        </div>
      </div>
      <div v-if="visible.length > 4" class="grid">
        <GroupBuyCard
          v-for="g in visible.slice(4)"
          :key="g.id"
          :group="g"
          @action="openJoin(g)"
          @click="goDetail(g)"
        />
      </div>
    </template>

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
            <div class="join-unit-price">
              拼团单价 <span class="fc-price"><i>¥</i>{{ formatMoney(current.price) }}</span>
            </div>
          </div>
        </div>

        <div class="join-row">
          <span class="join-label">购买数量</span>
          <el-input-number v-model="quantity" :min="1" :max="99" />
        </div>

        <div class="join-row">
          <span class="join-label">提货点</span>
          <span class="join-value">
            {{ current.pickupPointName || '—' }}
            <small v-if="current.pickupPointAddress">{{ current.pickupPointAddress }}</small>
          </span>
        </div>

        <div class="join-row join-total">
          <span class="join-label">应付合计</span>
          <strong class="join-value fc-price"><i>¥</i>{{ formatMoney(orderTotal) }}</strong>
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
import { Search } from '@element-plus/icons-vue'
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
const searchKeyword = ref('')
const selectedCategory = ref('全部')

/** 动态提取在团商品的所有分类 */
const categories = computed(() => {
  const set = new Set()
  all.value.forEach(g => {
    if (g.category) set.add(g.category)
  })
  return ['全部', ...Array.from(set)]
})

/** 列表排序：综合为接口默认顺序（新开团优先），截止时间按 deadline 升序（客户端排即可，数据集为进行中团） */
const SORTS = [
  { value: 'default', label: '综合' },
  { value: 'deadline', label: '即将截止' }
]
const sort = ref('default')

const dialogVisible = ref(false)
const submitting = ref(false)
const quantity = ref(1)
const current = ref(null)
const orderTotal = computed(() => Math.round(Number(current.value?.price || 0) * 100) * quantity.value / 100)

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
  let list = all.value
  if (filter.value === 'urgent') list = list.filter(isUrgent)
  if (filter.value === 'almost') list = list.filter(isAlmost)
  if (selectedCategory.value !== '全部') {
    list = list.filter(g => g.category === selectedCategory.value)
  }
  if (searchKeyword.value.trim()) {
    const kw = searchKeyword.value.trim().toLowerCase()
    list = list.filter(g =>
      (g.title && g.title.toLowerCase().includes(kw)) ||
      (g.productName && g.productName.toLowerCase().includes(kw))
    )
  }
  if (sort.value === 'deadline') {
    list = [...list].sort((a, b) => new Date(a.deadline) - new Date(b.deadline))
  }
  return list
})

function resetFilters() {
  filter.value = 'all'
  selectedCategory.value = '全部'
  searchKeyword.value = ''
}

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

/* 首页标题保留足够的空间，让商品与团购状态成为主角。 */
.plaza-intro {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 24px;
  padding: 8px 0 6px;
}
.intro-label {
  color: var(--fc-accent-600);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: .14em;
}
.plaza-intro h2 {
  margin: 8px 0 6px;
  color: var(--fc-ink-900);
  font-family: "Songti SC", "Microsoft YaHei", serif;
  font-size: clamp(23px, 2.4vw, 31px);
  line-height: 1.3;
}
.plaza-intro p { margin: 0; color: var(--fc-ink-500); font-size: 14px; }
.order-link {
  flex: none;
  border: 1px solid var(--fc-line);
  border-radius: var(--fc-r-sm);
  background: var(--fc-card);
  color: var(--fc-brand-700);
  padding: 10px 15px;
  font: inherit;
  font-weight: 600;
  cursor: pointer;
}
.order-link:hover { background: var(--fc-brand-50); }
.order-link span { margin-left: 8px; }

/* ---------- 搜索与分类控制栏 ---------- */
.plaza-controls {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.cat-tabs {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.cat-chip {
  display: inline-flex;
  align-items: center;
  padding: 5px 14px;
  border-radius: var(--fc-r-pill);
  font-size: 13px;
  color: var(--fc-ink-500);
  background: #fff;
  border: 1px solid var(--fc-line);
  cursor: pointer;
  transition: all .15s ease;
  user-select: none;
}

.cat-chip:hover {
  color: var(--fc-brand);
  border-color: var(--fc-brand);
}

.cat-chip.on {
  color: #fff;
  background: var(--fc-brand);
  border-color: var(--fc-brand);
  font-weight: 600;
  box-shadow: 0 2px 6px rgba(30, 158, 90, .25);
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

.sort-group {
  display: inline-flex;
  border: 1px solid var(--fc-line);
  background: #fff;
  border-radius: var(--fc-r-pill);
  overflow: hidden;
}

.sort-btn {
  border: none;
  background: none;
  font-family: inherit;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--fc-ink-400);
  height: 32px;
  padding: 0 14px;
  cursor: pointer;
  transition: color .15s ease, background .15s ease;
}

.sort-btn:hover {
  color: var(--fc-ink-700);
}

.sort-btn.on {
  color: var(--fc-brand-700);
  background: var(--fc-brand-50);
}

/* 一张主图与旁侧团购列表呼应社区生活的方向。 */
.featured-groups {
  display: grid;
  grid-template-columns: minmax(0, .95fr) minmax(0, 1.05fr);
  gap: 16px;
  align-items: stretch;
}
.featured-list { display: grid; gap: 12px; }
.featured-groups:has(> :only-child) { grid-template-columns: minmax(0, 620px); }
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

.join-unit-price { color: var(--fc-ink-500); font-size: 13px; }
.join-unit-price .fc-price { margin-left: 5px; font-size: 18px; }

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
  text-align: right;
}

.join-value small { display: block; color: var(--fc-ink-500); font-size: 12px; font-weight: 400; }
.join-total { padding-top: 14px; border-top: 1px solid var(--fc-line); }
.join-total .join-value { font-size: 21px; }

.join-note {
  padding: 11px 13px;
  border-radius: var(--fc-r);
  background: var(--fc-brand-50);
  border: 1px solid var(--fc-brand-100);
  font-size: 11.5px;
  color: var(--fc-brand-700);
  line-height: 1.7;
}

@media (max-width: 1000px) {
  .featured-groups { grid-template-columns: 1fr; }
  .featured-list { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 700px) {
  .plaza { gap: 15px; }
  .plaza-intro { align-items: flex-start; }
  .plaza-intro h2 { font-size: 23px; }
  /* 手机底部导航已有订单入口，避免标题被挤成单字换行。 */
  .order-link { display: none; }
  .featured-list { grid-template-columns: 1fr; }
  .grid { grid-template-columns: 1fr; }
}
</style>

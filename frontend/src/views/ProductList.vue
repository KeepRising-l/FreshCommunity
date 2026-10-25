<template>
  <div class="plist">
    <!-- 页头 -->
    <header class="page-head">
      <div>
        <h2>商品浏览</h2>
        <p>基地直采的生鲜商品，参团享受拼团价</p>
      </div>
    </header>

    <!-- 工具栏：搜索 + 排序 -->
    <div class="toolbar fc-card">
      <div class="search">
        <el-icon><Search /></el-icon>
        <input
          v-model="searchInput"
          placeholder="搜索商品名称，如：苹果、草莓…"
          @keyup.enter="applySearch"
        />
        <button v-if="searchInput" class="clear" title="清空" @click="clearSearch">×</button>
        <el-button type="primary" size="small" @click="applySearch">搜索</el-button>
      </div>

      <div class="sort">
        <span class="sort-label">排序</span>
        <button
          v-for="s in SORTS"
          :key="s.value"
          class="sort-item"
          :class="{ on: sort === s.value }"
          @click="changeSort(s.value)"
        >
          {{ s.label }}
        </button>
      </div>
    </div>

    <!-- 分类 chips -->
    <div class="cats">
      <button class="cat" :class="{ on: !category }" @click="changeCategory('')">全部</button>
      <button
        v-for="c in categories"
        :key="c"
        class="cat"
        :class="{ on: category === c }"
        @click="changeCategory(c)"
      >
        {{ c }}
      </button>
    </div>

    <!-- 结果提示 -->
    <div v-if="!loading && keyword" class="filter-tip">
      搜索「{{ keyword }}」<template v-if="category"> · 分类「{{ category }}」</template>
      ，共 {{ total }} 个结果
      <el-link type="primary" :underline="false" @click="clearAll">清除筛选</el-link>
    </div>

    <!-- 列表 -->
    <SkeletonList v-if="loading" :rows="3" variant="card" />

    <EmptyState
      v-else-if="products.length === 0"
      icon="🥬"
      title="没有找到相关商品"
      :description="hasFilter ? '换个关键词或分类试试，或清除筛选查看全部商品。' : '商品上架后会显示在这里。'"
      :action-text="hasFilter ? '清除筛选' : ''"
      @action="clearAll"
    />

    <div v-else class="grid">
      <article v-for="p in products" :key="p.id" class="pcard fc-card" @click="goDetail(p.id)">
        <div class="thumb" :class="visualOf(p).gradient">
          <img v-if="p.imageUrl" :src="p.imageUrl" :alt="p.name" loading="lazy" />
          <span v-else class="emoji">{{ visualOf(p).emoji }}</span>
          <span class="cat-tag">{{ p.category || '其他' }}</span>
        </div>
        <div class="body">
          <h4>{{ p.name }}</h4>
          <div class="meta">
            <b class="price fc-price"><i>¥</i>{{ formatMoney(p.price) }}</b>
            <span class="stock" :class="{ low: p.stock <= 10 }">
              {{ p.stock > 0 ? `剩 ${p.stock} 件` : '暂时缺货' }}
            </span>
          </div>
        </div>
      </article>
    </div>

    <!-- 分页 -->
    <div v-if="total > 0" class="pager">
      <el-pagination
        layout="total, prev, pager, next, jumper"
        :total="total"
        :current-page="page"
        :page-size="size"
        :pager-count="7"
        background
        @current-change="changePage"
      />
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import { listProducts, listCategories } from '../api/product'
import EmptyState from '../components/EmptyState.vue'
import SkeletonList from '../components/SkeletonList.vue'
import { productVisual } from '../utils/visual'
import { formatMoney } from '../utils/format'

const route = useRoute()
const router = useRouter()

const SORTS = [
  { value: 'latest', label: '最新上架' },
  { value: 'price_asc', label: '价格从低到高' },
  { value: 'price_desc', label: '价格从高到低' }
]

const products = ref([])
const categories = ref([])
const loading = ref(true)

// 检索条件（keyword 与 URL query 双向同步，便于顶栏搜索跳转进来）
const keyword = ref('')
const searchInput = ref('')
const category = ref('')
const sort = ref('latest')
const page = ref(1)
const size = 12
const total = ref(0)

const hasFilter = computed(() => !!keyword.value || !!category.value)

function visualOf(p) {
  return productVisual(p.name)
}

async function load() {
  loading.value = true
  try {
    const res = await listProducts({
      keyword: keyword.value || undefined,
      category: category.value || undefined,
      sort: sort.value,
      page: page.value,
      size
    })
    products.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (e) {
    products.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

/** 把当前筛选条件写回 URL（replace 不留历史，回退仍能出列表页） */
function syncQuery() {
  const query = {}
  if (keyword.value) query.keyword = keyword.value
  if (category.value) query.category = category.value
  router.replace({ path: '/user/products', query })
}

function applySearch() {
  keyword.value = searchInput.value.trim()
  page.value = 1
  syncQuery()
  load()
}

function clearSearch() {
  searchInput.value = ''
  applySearch()
}

function changeCategory(c) {
  category.value = c
  page.value = 1
  syncQuery()
  load()
}

function changeSort(s) {
  if (sort.value === s) return
  sort.value = s
  page.value = 1
  load()
}

function changePage(p) {
  page.value = p
  load()
}

function clearAll() {
  keyword.value = ''
  searchInput.value = ''
  category.value = ''
  page.value = 1
  syncQuery()
  load()
}

function goDetail(id) {
  router.push('/user/products/' + id)
}

/** 顶栏搜索 / 分享链接带 query 进入时同步条件（自身 replace 触发的变化跳过重复加载） */
watch(
  () => route.query,
  (q) => {
    const kw = String(q.keyword || '').trim()
    const cat = String(q.category || '').trim()
    if (kw === keyword.value && cat === category.value) return
    keyword.value = kw
    searchInput.value = kw
    category.value = cat
    page.value = 1
    load()
  }
)

onMounted(async () => {
  keyword.value = String(route.query.keyword || '').trim()
  searchInput.value = keyword.value
  category.value = String(route.query.category || '').trim()
  listCategories()
    .then((res) => (categories.value = res.data || []))
    .catch(() => (categories.value = []))
  await load()
})
</script>

<style scoped>
.plist {
  max-width: 1080px;
  margin: 0 auto;
}

.page-head {
  margin-bottom: 18px;
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

/* ---------- 工具栏 ---------- */
.toolbar {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 13px 16px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}

.search {
  flex: 1;
  min-width: 260px;
  max-width: 420px;
  height: 38px;
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 0 6px 0 14px;
  border-radius: var(--fc-r-pill);
  background: var(--fc-bg);
  border: 1px solid transparent;
  transition: border-color .18s ease, background .18s ease;
}

.search:focus-within {
  border-color: var(--fc-line);
  background: #fff;
}

.search .el-icon {
  color: var(--fc-ink-400);
  flex: none;
}

.search input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  font-family: inherit;
  font-size: 13.5px;
  color: var(--fc-ink-800);
}

.search input::placeholder {
  color: var(--fc-ink-300);
}

.search .clear {
  border: none;
  background: var(--fc-line);
  color: var(--fc-ink-500);
  width: 20px;
  height: 20px;
  border-radius: 50%;
  cursor: pointer;
  font-size: 13px;
  line-height: 1;
}

.sort {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-left: auto;
}

.sort-label {
  font-size: 12.5px;
  color: var(--fc-ink-400);
  margin-right: 6px;
}

.sort-item {
  border: none;
  background: none;
  font-family: inherit;
  font-size: 13px;
  font-weight: 600;
  color: var(--fc-ink-400);
  padding: 7px 12px;
  border-radius: var(--fc-r-pill);
  cursor: pointer;
  transition: color .15s ease, background .15s ease;
}

.sort-item:hover {
  color: var(--fc-ink-700);
}

.sort-item.on {
  color: var(--fc-brand-700);
  background: var(--fc-brand-50);
}

/* ---------- 分类 chips ---------- */
.cats {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 18px;
}

.cat {
  border: 1px solid var(--fc-line);
  background: var(--fc-card);
  font-family: inherit;
  font-size: 13px;
  font-weight: 600;
  color: var(--fc-ink-500);
  padding: 7px 16px;
  border-radius: var(--fc-r-pill);
  cursor: pointer;
  transition: all .15s ease;
}

.cat:hover {
  border-color: var(--fc-brand-100);
  color: var(--fc-brand-700);
}

.cat.on {
  background: var(--fc-brand-700);
  border-color: var(--fc-brand-700);
  color: #fff;
}

.filter-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 14px;
  font-size: 13px;
  color: var(--fc-ink-500);
}

/* ---------- 商品网格 ---------- */
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
  gap: 16px;
}

.pcard {
  overflow: hidden;
  cursor: pointer;
  transition: transform .18s ease, box-shadow .18s ease;
}

.pcard:hover {
  transform: translateY(-3px);
  box-shadow: var(--fc-sh-2);
}

.thumb {
  position: relative;
  height: 150px;
  display: grid;
  place-items: center;
}

.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.thumb .emoji {
  font-size: 56px;
}

.cat-tag {
  position: absolute;
  top: 10px;
  left: 10px;
  font-size: 11px;
  font-weight: 700;
  color: var(--fc-brand-700);
  background: rgba(255, 255, 255, .9);
  border-radius: var(--fc-r-pill);
  padding: 3px 10px;
  backdrop-filter: blur(4px);
}

.body {
  padding: 13px 15px 15px;
}

.body h4 {
  margin: 0 0 9px;
  font-size: 14.5px;
  font-weight: 650;
  color: var(--fc-ink-900);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.meta {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.price {
  font-size: 17px;
}

.stock {
  font-size: 12px;
  color: var(--fc-ink-400);
}

.stock.low {
  color: var(--fc-accent-600);
  font-weight: 600;
}

/* ---------- 分页 ---------- */
.pager {
  display: flex;
  justify-content: center;
  margin-top: 26px;
}
</style>

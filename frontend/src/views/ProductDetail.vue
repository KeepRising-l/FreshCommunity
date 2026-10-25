<template>
  <div class="pdetail">
    <!-- 面包屑 -->
    <nav class="crumbs">
      <router-link to="/user/products">商品浏览</router-link>
      <el-icon><ArrowRight /></el-icon>
      <span>{{ product.name || '…' }}</span>
    </nav>

    <SkeletonList v-if="loading" :rows="2" variant="card" />

    <EmptyState
      v-else-if="!product.id"
      icon="🥬"
      title="商品不存在"
      description="它可能已下架或被删除，去列表页看看其他商品吧。"
      action-text="返回商品浏览"
      @action="router.push('/user/products')"
    />

    <template v-else>
      <!-- 主信息区 -->
      <div class="hero fc-card">
        <div class="visual" :class="visual.gradient">
          <img v-if="product.imageUrl" :src="product.imageUrl" :alt="product.name" />
          <span v-else class="emoji">{{ visual.emoji }}</span>
        </div>

        <div class="info">
          <div class="tags">
            <span class="cat-tag">{{ product.category || '其他' }}</span>
            <span class="stock-tag" :class="{ low: product.stock <= 10 }">
              {{ product.stock > 0 ? `库存 ${product.stock} 件` : '暂时缺货' }}
            </span>
          </div>

          <h2>{{ product.name }}</h2>

          <div class="price-row">
            <div class="price-box">
              <span class="label">零售价</span>
              <b class="fc-price price"><i>¥</i>{{ formatMoney(product.price) }}</b>
            </div>
            <span class="price-tip">参加团购享拼团价，成团更划算</span>
          </div>

          <div class="rating-row">
            <el-rate :model-value="product.avgScore || 0" disabled />
            <span class="rating-text">{{ ratingText }}</span>
          </div>

          <div v-if="insightTags.length" class="insights">
            <span class="insights-label">大家都在说</span>
            <span v-for="tag in insightTags" :key="tag" class="insight-tag">{{ tag }}</span>
          </div>

          <div class="actions">
            <el-button type="primary" size="large" @click="router.push('/user/groups')">
              去团购广场参团
            </el-button>
            <el-button size="large" @click="router.push('/user/ai')">让 AI 导购帮我盯</el-button>
          </div>
        </div>
      </div>

      <!-- 描述 -->
      <section v-if="product.description" class="fc-card desc">
        <h3 class="fc-section-title">商品介绍</h3>
        <p>{{ product.description }}</p>
      </section>

      <!-- 评价 -->
      <section class="fc-card reviews">
        <div class="reviews-head">
          <h3 class="fc-section-title">用户评价{{ reviews.length ? `（${reviews.length}）` : '' }}</h3>
        </div>

        <EmptyState
          v-if="reviews.length === 0"
          icon="💬"
          title="还没有评价"
          description="完成一笔团购订单后就可以评价啦。"
        />

        <ul v-else class="review-list">
          <li v-for="r in reviews" :key="r.id" class="review">
            <div class="review-head">
              <div class="avatar">{{ (r.username || '?').charAt(0).toUpperCase() }}</div>
              <div class="who">
                <b>{{ r.username }}</b>
                <span>{{ fromNow(r.createdAt) }}</span>
              </div>
              <div class="scores">
                <span class="score-item">商品 <el-rate :model-value="r.productScore" disabled size="small" /></span>
                <span class="score-item">团长 <el-rate :model-value="r.leaderScore" disabled size="small" /></span>
              </div>
            </div>
            <p v-if="r.content" class="review-content">{{ r.content }}</p>
          </li>
        </ul>
      </section>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowRight } from '@element-plus/icons-vue'
import { getProduct } from '../api/product'
import { getProductReviews } from '../api/review'
import EmptyState from '../components/EmptyState.vue'
import SkeletonList from '../components/SkeletonList.vue'
import { productVisual } from '../utils/visual'
import { formatMoney, fromNow } from '../utils/format'

const route = useRoute()
const router = useRouter()

const product = ref({})
const reviews = ref([])
const loading = ref(true)

const insightTags = computed(() => product.value.insightTags || [])
const visual = computed(() => productVisual(product.value.name))

const ratingText = computed(() => {
  const count = product.value.reviewCount || 0
  if (count === 0) return '暂无评分'
  return `${product.value.avgScore} 分 · ${count} 条评价`
})

onMounted(async () => {
  const id = route.params.id
  try {
    const [p, r] = await Promise.all([getProduct(id), getProductReviews(id)])
    product.value = p.data || {}
    reviews.value = r.data?.reviews || []
  } catch (e) {
    product.value = {}
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.pdetail {
  max-width: 980px;
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

/* ---------- 主信息区 ---------- */
.hero {
  display: flex;
  gap: 28px;
  padding: 24px;
  margin-bottom: 16px;
}

.visual {
  position: relative;
  width: 300px;
  height: 260px;
  flex: none;
  border-radius: var(--fc-r);
  display: grid;
  place-items: center;
  overflow: hidden;
}

.visual img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.visual .emoji {
  font-size: 96px;
}

.info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.tags {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}

.cat-tag {
  font-size: 12px;
  font-weight: 700;
  color: var(--fc-brand-700);
  background: var(--fc-brand-50);
  padding: 3px 11px;
  border-radius: var(--fc-r-pill);
}

.stock-tag {
  font-size: 12px;
  font-weight: 600;
  color: var(--fc-ink-400);
  background: var(--fc-card-2);
  border: 1px solid var(--fc-line-soft);
  padding: 3px 11px;
  border-radius: var(--fc-r-pill);
}

.stock-tag.low {
  color: var(--fc-accent-600);
  background: var(--fc-accent-50);
  border-color: transparent;
}

.info h2 {
  margin: 0 0 14px;
  font-size: 22px;
  font-weight: 700;
  color: var(--fc-ink-900);
}

.price-row {
  display: flex;
  align-items: baseline;
  gap: 14px;
  margin-bottom: 14px;
}

.price-box .label {
  display: block;
  font-size: 12px;
  color: var(--fc-ink-400);
  margin-bottom: 3px;
}

.price {
  font-size: 28px;
}

.price-tip {
  font-size: 12.5px;
  color: var(--fc-ink-400);
}

.rating-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.rating-text {
  font-size: 13px;
  color: var(--fc-ink-500);
  font-weight: 600;
}

.insights {
  display: flex;
  align-items: center;
  gap: 7px;
  flex-wrap: wrap;
  margin-bottom: 18px;
}

.insights-label {
  font-size: 12.5px;
  color: var(--fc-ink-400);
}

.insight-tag {
  font-size: 12px;
  font-weight: 650;
  color: var(--fc-brand-700);
  background: var(--fc-brand-50);
  padding: 3px 10px;
  border-radius: var(--fc-r-pill);
}

.actions {
  margin-top: auto;
  display: flex;
  gap: 10px;
}

/* ---------- 描述 ---------- */
.desc {
  padding: 20px 24px;
  margin-bottom: 16px;
}

.desc p {
  margin: 12px 0 0;
  font-size: 14px;
  line-height: 1.9;
  color: var(--fc-ink-700);
}

/* ---------- 评价 ---------- */
.reviews {
  padding: 20px 24px;
}

.review-list {
  list-style: none;
  margin: 8px 0 0;
  padding: 0;
}

.review {
  padding: 16px 0;
  border-bottom: 1px dashed var(--fc-line-soft);
}

.review:last-child {
  border-bottom: none;
}

.review-head {
  display: flex;
  align-items: center;
  gap: 11px;
}

.avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  flex: none;
  display: grid;
  place-items: center;
  background: linear-gradient(140deg, #7ED0A4, #1E9E5A);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
}

.who {
  min-width: 0;
}

.who b {
  display: block;
  font-size: 13.5px;
  color: var(--fc-ink-800);
}

.who span {
  font-size: 11.5px;
  color: var(--fc-ink-400);
}

.scores {
  margin-left: auto;
  display: flex;
  gap: 14px;
}

.score-item {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: var(--fc-ink-400);
}

.review-content {
  margin: 10px 0 0 45px;
  font-size: 13.5px;
  line-height: 1.7;
  color: var(--fc-ink-700);
}

@media (max-width: 760px) {
  .hero {
    flex-direction: column;
  }

  .visual {
    width: 100%;
    height: 200px;
  }

  .scores {
    margin-left: 0;
    flex-direction: column;
    gap: 4px;
  }
}
</style>

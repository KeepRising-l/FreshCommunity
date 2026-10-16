<template>
  <div>
    <el-button size="small" @click="$router.back()">返回</el-button>

    <h2>{{ product.name }}</h2>
    <div class="price">￥{{ product.price }}</div>
    <div class="stock">剩余库存：{{ product.stock }}</div>

    <div class="rating">
      <el-rate :model-value="product.avgScore || 0" disabled />
      <span class="count">{{ ratingText }}</span>
    </div>

    <div v-if="insightTags.length" class="insights">
      <span class="insights-label">口碑</span>
      <el-tag v-for="tag in insightTags" :key="tag" type="success" effect="plain" size="small">
        {{ tag }}
      </el-tag>
    </div>

    <h3>用户评价</h3>
    <el-empty v-if="reviews.length === 0" description="暂无评价" />
    <el-card v-for="r in reviews" :key="r.id" class="review">
      <div class="row">
        <strong>{{ r.username }}</strong>
        <span class="time">{{ formatTime(r.createdAt) }}</span>
      </div>
      <div class="scores">
        商品 <el-rate :model-value="r.productScore" disabled size="small" />
        团长 <el-rate :model-value="r.leaderScore" disabled size="small" />
      </div>
      <div v-if="r.content" class="content">{{ r.content }}</div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getProduct } from '../api/product'
import { getProductReviews } from '../api/review'

const route = useRoute()
const product = ref({})
const reviews = ref([])

const insightTags = computed(() => product.value.insightTags || [])

const ratingText = computed(() => {
  const count = product.value.reviewCount || 0
  if (count === 0) return '暂无评分'
  return `${product.value.avgScore} 分 · ${count} 条评价`
})

async function load() {
  const id = route.params.id
  const p = await getProduct(id)
  product.value = p.data || {}
  const r = await getProductReviews(id)
  reviews.value = (r.data && r.data.reviews) || []
}

function formatTime(t) {
  if (!t) return ''
  return new Date(t).toLocaleString()
}

onMounted(load)
</script>

<style scoped>
.price {
  color: #f56c6c;
  font-size: 18px;
}
.stock {
  color: #909399;
  font-size: 12px;
  margin-top: 4px;
}
.rating {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 10px 0 4px;
}
.count {
  color: #606266;
  font-size: 13px;
}
.insights {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 8px 0 4px;
  flex-wrap: wrap;
}
.insights-label {
  color: #909399;
  font-size: 12px;
}
.review {
  margin-bottom: 8px;
}
.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.time {
  color: #909399;
  font-size: 12px;
}
.scores {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 6px;
  color: #909399;
  font-size: 12px;
}
.content {
  margin-top: 6px;
  color: #606266;
}
</style>

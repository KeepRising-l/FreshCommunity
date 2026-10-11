<template>
  <div>
    <h2>商品</h2>
    <p v-if="keyword" class="filter-tip">
      筛选「{{ keyword }}」，共 {{ products.length }} 个结果
      <el-link type="primary" :underline="false" @click="clearKeyword">清除</el-link>
    </p>
    <EmptyState
      v-if="products.length === 0"
      icon="🥬"
      :title="keyword ? '没有找到相关商品' : '暂无商品'"
      :description="keyword ? '换个关键词试试，或清除筛选查看全部商品。' : '商品上架后会显示在这里。'"
      :action-text="keyword ? '清除筛选' : ''"
      @action="clearKeyword"
    />
    <el-row :gutter="12">
      <el-col :span="8" v-for="p in products" :key="p.id">
        <el-card class="pcard" @click="goDetail(p.id)">
          <div class="pname">{{ p.name }}</div>
          <div class="pprice">￥{{ p.price }}</div>
          <div class="pstock">剩余库存：{{ p.stock }}</div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listProducts } from '../api/product'
import EmptyState from '../components/EmptyState.vue'

const all = ref([])
const route = useRoute()
const router = useRouter()

/** 顶栏搜索通过 ?keyword= 传入，此处按商品名做客户端过滤 */
const keyword = computed(() => String(route.query.keyword || '').trim())

const products = computed(() => {
  const kw = keyword.value
  if (!kw) return all.value
  const lower = kw.toLowerCase()
  return all.value.filter((p) => String(p.name || '').toLowerCase().includes(lower))
})

async function load() {
  const res = await listProducts()
  all.value = res.data || []
}

function clearKeyword() {
  router.push({ path: '/user/products' })
}

function goDetail(id) {
  router.push('/user/products/' + id)
}

watch(() => route.query.keyword, () => {})

onMounted(load)
</script>

<style scoped>
.filter-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 14px;
  font-size: 13px;
  color: var(--fc-ink-500);
}
.pcard {
  cursor: pointer;
  margin-bottom: 12px;
}
.pname {
  font-weight: 500;
}
.pprice {
  color: #f56c6c;
  margin-top: 6px;
}
.pstock {
  color: #909399;
  font-size: 12px;
  margin-top: 4px;
}
</style>

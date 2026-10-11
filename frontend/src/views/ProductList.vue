<template>
  <div>
    <h2>商品</h2>
    <el-empty v-if="products.length === 0" description="暂无商品" />
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
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { listProducts } from '../api/product'

const products = ref([])
const router = useRouter()

async function load() {
  const res = await listProducts()
  products.value = res.data || []
}

function goDetail(id) {
  router.push('/user/products/' + id)
}

onMounted(load)
</script>

<style scoped>
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

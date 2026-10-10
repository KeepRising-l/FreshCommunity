<template>
  <div>
    <h2>团购列表</h2>
    <el-card v-for="g in groups" :key="g.id" style="margin-bottom:12px;">
      <div><strong>{{ g.title }}</strong></div>
      <div>团长：{{ g.leaderName }}（{{ g.pickupPointName }} {{ g.pickupPointAddress }}）</div>
      <div>已支付：{{ g.memberCount }} / {{ g.targetCount }} 人</div>
      <div>截止：{{ formatDate(g.deadline) }}</div>
      <el-button type="primary" style="margin-top:8px;" @click="join(g)">立即参团</el-button>
    </el-card>

    <el-dialog v-model="dialogVisible" title="下单">
      <div>数量：<el-input-number v-model="quantity" :min="1" /></div>
      <template #footer>
        <el-button @click="dialogVisible=false">取消</el-button>
        <el-button type="primary" @click="submit">确认下单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { listGroupBuys } from '../api/groupBuy'
import { createOrder } from '../api/order'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'

const groups = ref([])
const dialogVisible = ref(false)
const quantity = ref(1)
const currentGroup = ref(null)
const router = useRouter()

async function load() {
  const res = await listGroupBuys()
  groups.value = res.data
}

function formatDate(d) {
  if (!d) return ''
  return new Date(d).toLocaleString()
}

function join(g) {
  currentGroup.value = g
  quantity.value = 1
  dialogVisible.value = true
}

async function submit() {
  await createOrder(currentGroup.value.id, quantity.value)
  ElMessage.success('下单成功，请去我的订单支付')
  dialogVisible.value = false
  router.push('/user/orders')
}

onMounted(load)
</script>

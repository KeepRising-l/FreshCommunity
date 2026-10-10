<template>
  <div>
    <h2>我的订单</h2>
    <el-table :data="orders">
      <el-table-column prop="orderNo" label="订单号" />
      <el-table-column prop="quantity" label="数量" />
      <el-table-column prop="amount" label="金额" />
      <el-table-column prop="status" label="状态" />
      <el-table-column prop="pickupCode" label="取货码" />
      <el-table-column label="操作">
        <template #default="scope">
          <el-button v-if="scope.row.status === 'PENDING_PAYMENT'" size="small" type="primary" @click="pay(scope.row.id)">支付</el-button>
          <el-button v-if="canCancel(scope.row.status)" size="small" @click="cancel(scope.row.id)">取消</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { listMyOrders, payOrder, cancelOrder } from '../api/order'
import { ElMessage } from 'element-plus'

const orders = ref([])

async function load() {
  const res = await listMyOrders()
  orders.value = res.data
}

function canCancel(status) {
  return status === 'PENDING_PAYMENT' || status === 'PENDING_GROUP'
}

async function pay(id) {
  await payOrder(id)
  ElMessage.success('支付成功')
  load()
}

async function cancel(id) {
  await cancelOrder(id)
  ElMessage.success('取消成功')
  load()
}

onMounted(load)
</script>

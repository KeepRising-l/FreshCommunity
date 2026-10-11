<template>
  <div>
    <h2>我的团购</h2>
    <el-button type="primary" @click="openDialog()">发起团购</el-button>
    <el-table :data="groups" style="margin-top:16px;">
      <el-table-column prop="title" label="标题" />
      <el-table-column prop="targetCount" label="成团人数" />
      <el-table-column prop="memberCount" label="已支付人数" />
      <el-table-column prop="status" label="状态" />
      <el-table-column prop="deadline" label="截止时间" />
      <el-table-column label="操作">
        <template #default="scope">
          <el-button size="small" @click="viewOrders(scope.row)">查看订单</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" title="发起团购">
      <el-form :model="form">
        <el-form-item label="标题">
          <el-input v-model="form.title" />
        </el-form-item>
        <el-form-item label="商品">
          <el-select v-model="form.productId" placeholder="选择商品">
            <el-option v-for="p in products" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="成团人数">
          <el-input-number v-model="form.targetCount" :min="1" />
        </el-form-item>
        <el-form-item label="截止时间">
          <el-date-picker v-model="form.deadline" type="datetime" placeholder="选择截止时间" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible=false">取消</el-button>
        <el-button type="primary" @click="submit">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="orderDialogVisible" title="订单列表">
      <el-table :data="orders">
        <el-table-column prop="orderNo" label="订单号" />
        <el-table-column prop="quantity" label="数量" />
        <el-table-column prop="amount" label="金额" />
        <el-table-column prop="status" label="状态" />
        <el-table-column prop="pickupCode" label="取货码" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, reactive } from 'vue'
import { listMyGroupBuys, createGroupBuy, listGroupBuyOrders } from '../api/groupBuy'
import { listProducts } from '../api/product'

const groups = ref([])
const products = ref([])
const dialogVisible = ref(false)
const orderDialogVisible = ref(false)
const orders = ref([])
const form = reactive({ title: '', productId: null, targetCount: 1, deadline: '' })

async function load() {
  const res = await listMyGroupBuys()
  groups.value = res.data
}

async function loadProducts() {
  const res = await listProducts()
  products.value = res.data
}

function openDialog() {
  Object.assign(form, { title: '', productId: null, targetCount: 1, deadline: '' })
  dialogVisible.value = true
}

async function submit() {
  await createGroupBuy(form)
  dialogVisible.value = false
  load()
}

async function viewOrders(row) {
  const res = await listGroupBuyOrders(row.id)
  orders.value = res.data || []
  orderDialogVisible.value = true
}

onMounted(() => {
  load()
  loadProducts()
})
</script>

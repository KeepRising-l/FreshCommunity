<template>
  <div>
    <h2>数据看板</h2>
    <el-row :gutter="20" style="margin-top:20px;">
      <el-col :span="8">
        <el-card>
          <div style="font-size:28px;font-weight:bold;">{{ data.todayOrders || 0 }}</div>
          <div>今日订单数</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card>
          <div style="font-size:28px;font-weight:bold;">{{ data.todaySuccessGroups || 0 }}</div>
          <div>今日成团数</div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getDashboard } from '../api/admin'

const data = ref({})

onMounted(async () => {
  const res = await getDashboard()
  data.value = res.data
})
</script>

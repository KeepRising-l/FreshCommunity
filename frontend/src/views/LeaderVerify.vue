<template>
  <div>
    <h2>订单核销</h2>
    <el-input v-model="code" placeholder="请输入6位取货码" maxlength="6" style="width:240px;" />
    <el-button type="primary" style="margin-left:8px;" @click="verify">核销</el-button>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { verifyPickupCode } from '../api/order'
import { ElMessage } from 'element-plus'

const code = ref('')

async function verify() {
  await verifyPickupCode(code.value)
  ElMessage.success('核销成功')
  code.value = ''
}
</script>

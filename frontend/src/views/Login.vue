<template>
  <div class="login-page">
    <el-card class="login-card" title="社区生鲜团购平台">
      <h2 style="text-align:center;margin-bottom:24px;">社区生鲜团购平台</h2>
      <el-form :model="form" label-width="60px">
        <el-form-item label="账号">
          <el-input v-model="form.username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" style="width:100%;" @click="login">登录</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import request from '../utils/request'

const form = reactive({ username: '', password: '' })
const router = useRouter()
const store = useUserStore()

async function login() {
  const res = await request.post('/auth/login', form)
  store.setUser(res.data.user, res.data.token)
  router.push('/dashboard')
}
</script>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
}
.login-card {
  width: 360px;
}
</style>

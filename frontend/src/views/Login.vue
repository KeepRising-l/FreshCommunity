<template>
  <div class="login-page">
    <el-card class="login-card" title="社区生鲜团购平台">
      <h2 style="text-align:center;margin-bottom:24px;">社区生鲜团购平台</h2>
      <el-form :model="form" label-width="60px">
        <el-form-item label="账号">
          <el-input v-model="form.username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" @keyup.enter="login" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" style="width:100%;" :loading="loading" @click="login">登录</el-button>
        </el-form-item>
      </el-form>
      <div style="text-align:center;">
        <el-link type="primary" @click="router.push('/register')">没有账号？去注册</el-link>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { login as loginApi } from '../api/auth'

const form = reactive({ username: '', password: '' })
const loading = ref(false)
const router = useRouter()
const store = useUserStore()

async function login() {
  loading.value = true
  try {
    const res = await loginApi(form)
    store.setUser(res.data.user, res.data.token)
    router.push('/dashboard')
  } catch (e) {
    // 错误提示已由请求拦截器统一处理
  } finally {
    loading.value = false
  }
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

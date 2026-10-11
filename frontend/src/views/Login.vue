<template>
  <div class="login-page">
    <div class="login-card">
      <div class="brand">
        <div class="mark">🥬</div>
        <div>
          <b>FreshCommunity</b>
          <small>社区生鲜团购平台</small>
        </div>
      </div>

      <h2>欢迎回来</h2>
      <p class="sub">登录后即可参团、查看订单与取货码</p>

      <el-form :model="form" label-position="top">
        <el-form-item label="账号">
          <el-input v-model="form.username" size="large" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            size="large"
            placeholder="请输入密码"
            show-password
            @keyup.enter="login"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            size="large"
            class="submit"
            :loading="loading"
            @click="login"
          >
            登录
          </el-button>
        </el-form-item>
      </el-form>

      <div class="foot">
        没有账号？<el-link type="primary" :underline="false" @click="router.push('/register')">立即注册</el-link>
      </div>

      <div class="demo">
        <b>演示账号</b>（密码均为 <code>123456</code>）<br />
        用户 <code>user1</code> · 团长 <code>leader1</code> · 管理员 <code>admin</code>
      </div>
    </div>
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
  position: relative;
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  overflow: hidden;
  background: linear-gradient(150deg, #1E9E5A 0%, #12713F 62%, #0C5530 100%);
}

.login-page::before {
  content: "";
  position: absolute;
  width: 560px;
  height: 560px;
  border-radius: 50%;
  right: -180px;
  top: -200px;
  background: radial-gradient(circle, rgba(255, 255, 255, .16), transparent 68%);
}

.login-page::after {
  content: "";
  position: absolute;
  width: 380px;
  height: 380px;
  border-radius: 50%;
  left: -150px;
  bottom: -170px;
  background: radial-gradient(circle, rgba(255, 122, 26, .30), transparent 66%);
}

.login-card {
  position: relative;
  z-index: 2;
  width: 100%;
  max-width: 400px;
  padding: 34px 34px 28px;
  background: #fff;
  border-radius: var(--fc-r-xl);
  box-shadow: 0 24px 60px rgba(0, 0, 0, .26);
}

.brand {
  display: flex;
  align-items: center;
  gap: 11px;
  margin-bottom: 26px;
}

.brand .mark {
  width: 40px;
  height: 40px;
  border-radius: 13px;
  flex: none;
  display: grid;
  place-items: center;
  font-size: 20px;
  background: linear-gradient(140deg, #2BB673, #12713F);
  box-shadow: 0 4px 12px rgba(30, 158, 90, .3);
}

.brand b {
  display: block;
  font-size: 15px;
  font-weight: 650;
  color: var(--fc-ink-900);
  line-height: 1.25;
}

.brand small {
  display: block;
  font-size: 11px;
  color: var(--fc-ink-400);
}

h2 {
  margin: 0 0 6px;
  font-size: 23px;
  font-weight: 700;
  color: var(--fc-ink-900);
  letter-spacing: -.3px;
}

.sub {
  margin: 0 0 22px;
  font-size: 13px;
  color: var(--fc-ink-400);
}

.submit {
  width: 100%;
}

.foot {
  text-align: center;
  font-size: 13px;
  color: var(--fc-ink-400);
  margin-top: 4px;
}

.demo {
  margin-top: 20px;
  padding: 11px 14px;
  border-radius: var(--fc-r);
  background: var(--fc-bg);
  border: 1px dashed var(--fc-line);
  font-size: 11.5px;
  color: var(--fc-ink-500);
  line-height: 1.85;
}

.demo b {
  color: var(--fc-ink-700);
}

.demo code {
  background: #fff;
  border: 1px solid var(--fc-line);
  border-radius: 5px;
  padding: 1px 6px;
  font-size: 11px;
  color: var(--fc-brand-700);
}

:deep(.el-form-item__label) {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--fc-ink-700);
  padding-bottom: 6px;
}
</style>

<template>
  <div class="login-page">
    <div class="auth-shell">
      <aside class="auth-story" aria-label="社区生鲜团购">
        <div class="story-copy">
          <span>FRESH COMMUNITY</span>
          <h1>一日三餐，<br />和邻居一起更有滋味。</h1>
          <p>挑选新鲜好物，成团后到社区提货点自提。</p>
        </div>
      </aside>
    <div class="login-card">
      <div class="brand">
        <div class="mark" aria-hidden="true">✳</div>
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
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { login as loginApi } from '../api/auth'

const form = reactive({ username: '', password: '' })
const loading = ref(false)
const router = useRouter()
const route = useRoute()
const store = useUserStore()

async function login() {
  loading.value = true
  try {
    const res = await loginApi(form)
    store.setUser(res.data.user, res.data.token)
    // 路由守卫未登录时会带上 redirect 来路，登录成功后优先回跳
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    router.push(redirect && redirect.startsWith('/') ? redirect : '/dashboard')
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
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
  background: var(--fc-bg);
}

.login-card {
  position: relative;
  z-index: 2;
  width: 100%;
  padding: 46px 44px;
  background: var(--fc-card);
  align-self: center;
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
  background: var(--fc-brand);
  color: #fff;
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

/* 邻里生活：用真实商品照片和温暖的纸色承载登录表单。 */
.auth-shell {
  width: min(1020px, 100%);
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(380px, 450px);
  overflow: hidden;
  border: 1px solid var(--fc-line);
  border-radius: 18px;
  background: var(--fc-card);
  box-shadow: 0 18px 48px rgba(45, 54, 38, .09);
}
.auth-story {
  min-height: 620px;
  display: flex;
  align-items: flex-end;
  padding: 42px;
  color: #fff;
  background: linear-gradient(180deg, rgba(20, 39, 27, .05) 20%, rgba(20, 39, 27, .78)), url('/images/products/p18.jpg') center / cover;
}
.story-copy { max-width: 420px; }
.story-copy span { font-size: 12px; font-weight: 700; letter-spacing: .14em; color: #f3d8bb; }
.story-copy h1 { margin: 14px 0 12px; font-size: clamp(26px, 3vw, 36px); line-height: 1.35; }
.story-copy p { margin: 0; font-size: 14px; line-height: 1.7; color: #f4f2e9; }
.sub, .foot { color: var(--fc-ink-500); }
.demo { background: var(--fc-card-2); border-color: var(--fc-line); font-size: 12px; }
.demo code { background: var(--fc-card); font-size: 12px; }
@media (max-width: 760px) {
  .login-page { padding: 18px; }
  .auth-shell { display: block; max-width: 480px; }
  .auth-story { min-height: 190px; padding: 24px; }
  .story-copy h1 { margin: 8px 0; font-size: 24px; }
  .story-copy p { font-size: 13px; }
  .login-card { padding: 30px 24px; }
}
</style>

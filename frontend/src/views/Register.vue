<template>
  <div class="register-page">
    <div class="register-card">
      <div class="brand">
        <div class="mark">🥬</div>
        <div>
          <b>FreshCommunity</b>
          <small>社区生鲜团购平台</small>
        </div>
      </div>

      <h2>创建账号</h2>
      <p class="sub">注册即可参团，团长可发起团购与核销</p>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="账号" prop="username">
          <el-input v-model="form.username" size="large" placeholder="3-20 个字符" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" size="large" show-password placeholder="6-32 个字符" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            size="large"
            show-password
            placeholder="再次输入密码"
            @keyup.enter="submit"
          />
        </el-form-item>

        <el-form-item label="身份" prop="role">
          <div class="role-picker">
            <button
              type="button"
              class="role-card"
              :class="{ on: form.role === 'USER' }"
              @click="form.role = 'USER'"
            >
              <span class="role-icon">🧺</span>
              <b>普通用户</b>
              <small>浏览参团 · 下单自提</small>
            </button>
            <button
              type="button"
              class="role-card"
              :class="{ on: form.role === 'LEADER' }"
              @click="form.role = 'LEADER'"
            >
              <span class="role-icon">🚩</span>
              <b>团长</b>
              <small>发起团购 · 核销取货</small>
            </button>
          </div>
        </el-form-item>

        <template v-if="form.role === 'LEADER'">
          <el-form-item label="提货点名称" prop="pickupPointName">
            <el-input v-model="form.pickupPointName" size="large" placeholder="如：幸福小区便利店" />
          </el-form-item>
          <el-form-item label="提货点地址">
            <el-input v-model="form.pickupPointAddress" size="large" placeholder="选填，如：幸福小区东门 101 号" />
          </el-form-item>
        </template>

        <el-form-item>
          <el-button type="primary" size="large" class="submit" :loading="loading" @click="submit">
            注册并登录
          </el-button>
        </el-form-item>
      </el-form>

      <div class="foot">
        已有账号？<el-link type="primary" :underline="false" @click="router.push('/login')">去登录</el-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../stores/user'
import { register } from '../api/auth'

const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  role: 'USER',
  pickupPointName: '',
  pickupPointAddress: ''
})

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度需在 3-20 个字符之间', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度需在 6-32 个字符之间', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== form.password) callback(new Error('两次输入的密码不一致'))
        else callback()
      },
      trigger: 'blur'
    }
  ],
  pickupPointName: [{ required: true, message: '团长需填写提货点名称', trigger: 'blur' }]
}

const formRef = ref()
const loading = ref(false)
const router = useRouter()
const store = useUserStore()

async function submit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const payload = {
      username: form.username,
      password: form.password,
      role: form.role
    }
    if (form.role === 'LEADER') {
      payload.pickupPointName = form.pickupPointName
      payload.pickupPointAddress = form.pickupPointAddress
    }
    const res = await register(payload)
    store.setUser(res.data.user, res.data.token)
    ElMessage.success('注册成功，欢迎加入')
    router.push('/dashboard')
  } catch (e) {
    // 错误提示已由请求拦截器统一处理
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.register-page {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  overflow: hidden;
  background: linear-gradient(150deg, #1E9E5A 0%, #12713F 62%, #0C5530 100%);
}

.register-page::before {
  content: "";
  position: absolute;
  width: 560px;
  height: 560px;
  border-radius: 50%;
  right: -180px;
  top: -200px;
  background: radial-gradient(circle, rgba(255, 255, 255, .16), transparent 68%);
}

.register-page::after {
  content: "";
  position: absolute;
  width: 380px;
  height: 380px;
  border-radius: 50%;
  left: -150px;
  bottom: -170px;
  background: radial-gradient(circle, rgba(255, 122, 26, .30), transparent 66%);
}

.register-card {
  position: relative;
  z-index: 2;
  width: 100%;
  max-width: 420px;
  padding: 34px 34px 28px;
  background: #fff;
  border-radius: var(--fc-r-xl);
  box-shadow: 0 24px 60px rgba(0, 0, 0, .26);
  max-height: 94vh;
  overflow-y: auto;
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

/* ---------- 身份选择卡 ---------- */
.role-picker {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  width: 100%;
}

.role-card {
  border: 1.5px solid var(--fc-line);
  background: var(--fc-card-2);
  border-radius: var(--fc-r);
  padding: 13px 12px;
  cursor: pointer;
  text-align: left;
  font-family: inherit;
  transition: border-color .15s ease, background .15s ease, box-shadow .15s ease;
}

.role-card:hover {
  border-color: var(--fc-brand-100);
}

.role-card.on {
  border-color: var(--fc-brand);
  background: var(--fc-brand-50);
  box-shadow: 0 0 0 3px rgba(30, 158, 90, .1);
}

.role-icon {
  font-size: 22px;
  display: block;
  margin-bottom: 7px;
}

.role-card b {
  display: block;
  font-size: 13.5px;
  font-weight: 700;
  color: var(--fc-ink-900);
  margin-bottom: 2px;
}

.role-card small {
  font-size: 11px;
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

:deep(.el-form-item__label) {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--fc-ink-700);
  padding-bottom: 6px;
}
</style>

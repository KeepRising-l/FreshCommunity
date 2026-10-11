<template>
  <div class="register-page">
    <el-card class="register-card">
      <h2 style="text-align:center;margin-bottom:24px;">注册账号</h2>
      <el-form :model="form" label-width="80px" :rules="rules" ref="formRef">
        <el-form-item label="账号" prop="username">
          <el-input v-model="form.username" placeholder="3-20 个字符" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="6-32 个字符" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" placeholder="再次输入密码" />
        </el-form-item>
        <el-form-item label="身份" prop="role">
          <el-radio-group v-model="form.role">
            <el-radio label="USER">普通用户</el-radio>
            <el-radio label="LEADER">团长</el-radio>
          </el-radio-group>
        </el-form-item>
        <template v-if="form.role === 'LEADER'">
          <el-form-item label="提货点" prop="pickupPointName">
            <el-input v-model="form.pickupPointName" placeholder="提货点名称，如：幸福小区便利店" />
          </el-form-item>
          <el-form-item label="提货地址">
            <el-input v-model="form.pickupPointAddress" placeholder="提货点地址（选填）" />
          </el-form-item>
        </template>
        <el-form-item>
          <el-button type="primary" style="width:100%;" :loading="loading" @click="submit">注册</el-button>
        </el-form-item>
      </el-form>
      <div style="text-align:center;">
        <el-link type="primary" @click="router.push('/login')">已有账号？去登录</el-link>
      </div>
    </el-card>
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
  role: [{ required: true, message: '请选择身份', trigger: 'change' }],
  pickupPointName: [{ required: true, message: '团长需填写提货点名称', trigger: 'blur' }]
}

const formRef = ref()
const loading = ref(false)
const router = useRouter()
const store = useUserStore()

async function submit() {
  await formRef.value.validate()
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
    ElMessage.success('注册成功')
    router.push('/dashboard')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.register-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
  padding: 24px 0;
}
.register-card {
  width: 400px;
}
</style>

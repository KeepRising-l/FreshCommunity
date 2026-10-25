<template>
  <div class="profile">
    <!-- 账号信息 -->
    <section class="fc-card account">
      <div class="avatar">{{ avatarChar }}</div>
      <div class="account-info">
        <h2>
          {{ me?.username || store.user?.username || '…' }}
          <span class="role-tag" :class="roleClass">{{ roleText }}</span>
        </h2>
        <p class="fc-muted">
          注册于 {{ me?.createdAt ? formatDate(me.createdAt) : '—' }}
          <template v-if="isLeader">
            · 提货点「{{ me?.pickupPointName || '未设置' }}」
          </template>
        </p>
      </div>
    </section>

    <div class="cols" :class="{ single: !isLeader }">
      <!-- 修改密码 -->
      <section class="fc-card block">
        <h3 class="fc-section-title">修改密码</h3>
        <p class="fc-muted block-desc">修改成功后请牢记新密码，下次登录即生效</p>

        <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="88px" class="form">
          <el-form-item label="旧密码" prop="oldPassword">
            <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="当前登录密码" />
          </el-form-item>
          <el-form-item label="新密码" prop="newPassword">
            <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="6-32 位" />
          </el-form-item>
          <el-form-item label="确认新密码" prop="confirmPassword">
            <el-input v-model="pwdForm.confirmPassword" type="password" show-password placeholder="再输入一次新密码" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="pwdSubmitting" @click="submitPassword">保存新密码</el-button>
          </el-form-item>
        </el-form>
      </section>

      <!-- 团长：提货点维护 -->
      <section v-if="isLeader" class="fc-card block">
        <h3 class="fc-section-title">提货点信息</h3>
        <p class="fc-muted block-desc">邻居参团后到你设置的提货点自提，改动即时生效于你的团购</p>

        <el-form ref="pointFormRef" :model="pointForm" :rules="pointRules" label-width="88px" class="form">
          <el-form-item label="提货点名称" prop="pickupPointName">
            <el-input v-model="pointForm.pickupPointName" maxlength="128" placeholder="如：幸福小区便利店" />
          </el-form-item>
          <el-form-item label="提货点地址" prop="pickupPointAddress">
            <el-input v-model="pointForm.pickupPointAddress" maxlength="255" placeholder="如：幸福小区东门 101 号" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="pointSubmitting" @click="submitPoint">保存提货点</el-button>
          </el-form-item>
        </el-form>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../stores/user'
import { getMe, changePassword, updateProfile } from '../api/auth'
import { formatDate } from '../utils/format'

const store = useUserStore()

const me = ref(null)
const pwdFormRef = ref(null)
const pointFormRef = ref(null)
const pwdSubmitting = ref(false)
const pointSubmitting = ref(false)

const isLeader = computed(() => (me.value?.role || store.role) === 'LEADER')
const avatarChar = computed(() =>
  (me.value?.username || store.user?.username || '?').charAt(0).toUpperCase()
)
const ROLE_TEXT = { ADMIN: '管理员', LEADER: '团长', USER: '用户' }
const roleText = computed(() => ROLE_TEXT[me.value?.role || store.role] || '用户')
const roleClass = computed(() => (me.value?.role || store.role).toLowerCase())

const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const pwdRules = {
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '长度须为 6-32 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== pwdForm.newPassword) callback(new Error('两次输入的新密码不一致'))
        else callback()
      },
      trigger: 'blur'
    }
  ]
}

const pointForm = reactive({ pickupPointName: '', pickupPointAddress: '' })

const pointRules = {
  pickupPointName: [
    { required: true, message: '提货点名称不能为空', trigger: 'blur' },
    { max: 128, message: '不能超过 128 字', trigger: 'blur' }
  ],
  pickupPointAddress: [{ max: 255, message: '不能超过 255 字', trigger: 'blur' }]
}

async function loadMe() {
  try {
    const res = await getMe()
    me.value = res.data || null
    if (me.value) {
      pointForm.pickupPointName = me.value.pickupPointName || ''
      pointForm.pickupPointAddress = me.value.pickupPointAddress || ''
    }
  } catch (e) {
    me.value = store.user
  }
}

async function submitPassword() {
  const valid = await pwdFormRef.value.validate().catch(() => false)
  if (!valid) return
  pwdSubmitting.value = true
  try {
    await changePassword({
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword
    })
    ElMessage.success('密码已修改，请牢记新密码')
    pwdForm.oldPassword = ''
    pwdForm.newPassword = ''
    pwdForm.confirmPassword = ''
    pwdFormRef.value.clearValidate()
  } catch (e) {
    // 错误提示已由请求拦截器统一处理
  } finally {
    pwdSubmitting.value = false
  }
}

async function submitPoint() {
  const valid = await pointFormRef.value.validate().catch(() => false)
  if (!valid) return
  pointSubmitting.value = true
  try {
    const res = await updateProfile({
      pickupPointName: pointForm.pickupPointName.trim(),
      pickupPointAddress: pointForm.pickupPointAddress.trim()
    })
    ElMessage.success('提货点已更新')
    me.value = res.data || me.value
  } catch (e) {
    // 错误提示已由请求拦截器统一处理
  } finally {
    pointSubmitting.value = false
  }
}

onMounted(loadMe)
</script>

<style scoped>
.profile {
  max-width: 880px;
  margin: 0 auto;
}

.account {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 22px 26px;
  margin-bottom: 16px;
}

.avatar {
  width: 58px;
  height: 58px;
  border-radius: 50%;
  flex: none;
  display: grid;
  place-items: center;
  background: linear-gradient(140deg, #7ED0A4, #1E9E5A);
  color: #fff;
  font-size: 22px;
  font-weight: 700;
  box-shadow: 0 4px 14px rgba(30, 158, 90, .3);
}

.account-info h2 {
  margin: 0 0 5px;
  font-size: 19px;
  font-weight: 700;
  color: var(--fc-ink-900);
  display: flex;
  align-items: center;
  gap: 10px;
}

.role-tag {
  font-size: 11.5px;
  font-weight: 700;
  padding: 3px 10px;
  border-radius: var(--fc-r-pill);
}

.role-tag.admin {
  color: var(--fc-purple);
  background: var(--fc-purple-50);
}

.role-tag.leader {
  color: var(--fc-accent-600);
  background: var(--fc-accent-50);
}

.role-tag.user {
  color: var(--fc-brand-700);
  background: var(--fc-brand-50);
}

.cols {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  align-items: start;
}

.cols.single {
  grid-template-columns: 1fr;
  max-width: 460px;
}

.block {
  padding: 20px 24px;
}

.block-desc {
  margin: 6px 0 16px;
}

.form {
  max-width: 420px;
}

@media (max-width: 760px) {
  .cols {
    grid-template-columns: 1fr;
  }
}
</style>

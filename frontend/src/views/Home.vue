<template>
  <div>
    <h1>欢迎，{{ store.user?.username }}</h1>
    <p>角色：{{ roleText }}</p>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useUserStore } from '../stores/user'

const store = useUserStore()
const roleText = computed(() => {
  const map = { ADMIN: '管理员', LEADER: '团长', USER: '用户' }
  return map[store.role] || store.role
})
</script>

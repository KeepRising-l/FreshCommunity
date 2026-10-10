<template>
  <div>
    <el-container>
      <el-aside width="200px">
        <h3 style="padding:16px;">生鲜团购</h3>
        <el-menu :default-active="$route.path" router>
          <el-menu-item v-if="isAdmin" index="/admin/products">商品管理</el-menu-item>
          <el-menu-item v-if="isAdmin" index="/admin/dashboard">数据看板</el-menu-item>
          <el-menu-item v-if="isLeader" index="/leader/groups">我的团购</el-menu-item>
          <el-menu-item v-if="isLeader" index="/leader/verify">核销</el-menu-item>
          <el-menu-item v-if="isUser" index="/user/groups">团购列表</el-menu-item>
          <el-menu-item v-if="isUser" index="/user/orders">我的订单</el-menu-item>
          <el-menu-item @click="logout">退出登录</el-menu-item>
        </el-menu>
      </el-aside>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'

const store = useUserStore()
const router = useRouter()

const isAdmin = computed(() => store.role === 'ADMIN')
const isLeader = computed(() => store.role === 'LEADER')
const isUser = computed(() => store.role === 'USER')

function logout() {
  store.clearUser()
  router.push('/login')
}
</script>

<style scoped>
.el-container {
  height: 100vh;
}
.el-aside {
  background: #fff;
  border-right: 1px solid #e4e7ed;
}
</style>

<template>
  <div>
    <div class="header">
      <h2 style="margin:0;">我的消息</h2>
      <el-button size="small" :disabled="unreadStore.unread === 0" @click="readAll">全部已读</el-button>
    </div>

    <el-empty v-if="list.length === 0" description="暂无消息" />

    <el-card v-for="n in list" :key="n.id" class="item" :class="{ unread: n.isRead === 0 }">
      <div class="row">
        <strong>{{ n.title }}</strong>
        <span class="time">{{ formatTime(n.createdAt) }}</span>
      </div>
      <div class="content">{{ n.content }}</div>
      <el-button v-if="n.isRead === 0" size="small" text type="primary" @click="readOne(n.id)">标记已读</el-button>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { listNotifications, markNotificationRead, markAllNotificationsRead } from '../api/notification'
import { useNotificationStore } from '../stores/notification'
import { ElMessage } from 'element-plus'

const list = ref([])
const unreadStore = useNotificationStore()

async function load() {
  const res = await listNotifications()
  list.value = res.data || []
  unreadStore.refresh()
}

function formatTime(t) {
  if (!t) return ''
  return new Date(t).toLocaleString()
}

async function readOne(id) {
  await markNotificationRead(id)
  await load()
}

async function readAll() {
  await markAllNotificationsRead()
  ElMessage.success('已全部标记为已读')
  await load()
}

onMounted(load)
</script>

<style scoped>
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.item {
  margin-bottom: 8px;
}
.item.unread {
  border-left: 3px solid var(--fc-brand);
  background: #F7FCF9;
}
.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.time {
  color: #909399;
  font-size: 12px;
}
.content {
  margin-top: 6px;
  color: #606266;
}
</style>

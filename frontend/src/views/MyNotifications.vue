<template>
  <div class="noti">
    <!-- 筛选 -->
    <div class="filters">
      <span
        v-for="f in filters"
        :key="f.value"
        class="chip"
        :class="{ on: filter === f.value }"
        @click="filter = f.value"
      >
        {{ f.label }}
        <b v-if="f.count">{{ f.count }}</b>
      </span>
      <span class="spacer" />
      <el-button
        size="small"
        :disabled="unreadStore.unread === 0"
        @click="readAll"
      >
        全部标为已读
      </el-button>
    </div>

    <SkeletonList v-if="loading" :rows="4" variant="row" />

    <EmptyState
      v-else-if="visible.length === 0"
      icon="🔔"
      :title="list.length === 0 ? '还没有消息' : '这里没有消息'"
      :description="list.length === 0 ? '成团、核销、退款等事件会在这里通知你。' : '换个分类看看。'"
      :action-text="list.length === 0 ? '' : '查看全部'"
      @action="filter = 'all'"
    />

    <div v-else class="list">
      <div
        v-for="n in visible"
        :key="n.id"
        class="msg"
        :class="{ unread: n.isRead === 0 }"
        @click="readOne(n)"
      >
        <div class="ic" :class="'tone-' + typeOf(n).tone">{{ typeOf(n).icon }}</div>
        <div class="body">
          <div class="r1">
            <h4>{{ n.title || typeOf(n).text }}</h4>
            <span class="time">{{ fromNow(n.createdAt, now) }}</span>
          </div>
          <p>{{ n.content }}</p>
        </div>
        <span v-if="n.isRead === 0" class="undot" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import SkeletonList from '../components/SkeletonList.vue'
import EmptyState from '../components/EmptyState.vue'
import {
  listNotifications,
  markNotificationRead,
  markAllNotificationsRead
} from '../api/notification'
import { useNotificationStore } from '../stores/notification'
import { useNow } from '../composables/useNow'
import { notificationType } from '../constants/status'
import { fromNow } from '../utils/format'

const unreadStore = useNotificationStore()
const now = useNow()

const list = ref([])
const loading = ref(true)
const filter = ref('all')

const typeOf = (n) => notificationType(n.type)

/** 分类定义：把 5 类事件按用户关心的问题归成 3 组 */
const GROUP_MAP = {
  success: ['GROUP_SUCCESS'],
  failed: ['GROUP_FAILED', 'ORDER_TIMEOUT', 'REFUNDED'],
  pickup: ['PICKUP_DONE']
}

const filters = computed(() => {
  const count = (value) => {
    if (value === 'all') return list.value.length
    if (value === 'unread') return list.value.filter((n) => n.isRead === 0).length
    const types = GROUP_MAP[value] || []
    return list.value.filter((n) => types.includes(n.type)).length
  }
  return [
    { value: 'all', label: '全部', count: count('all') },
    { value: 'unread', label: '未读', count: count('unread') },
    { value: 'success', label: '成团通知', count: count('success') },
    { value: 'failed', label: '退款 / 关单', count: count('failed') },
    { value: 'pickup', label: '核销通知', count: count('pickup') }
  ]
})

const visible = computed(() => {
  if (filter.value === 'all') return list.value
  if (filter.value === 'unread') return list.value.filter((n) => n.isRead === 0)
  const types = GROUP_MAP[filter.value] || []
  return list.value.filter((n) => types.includes(n.type))
})

async function load() {
  loading.value = true
  try {
    const res = await listNotifications()
    list.value = res.data || []
    unreadStore.refresh()
  } catch (e) {
    list.value = []
  } finally {
    loading.value = false
  }
}

/** 点击消息即标记已读（比单独的按钮更符合习惯） */
async function readOne(n) {
  if (n.isRead !== 0) return
  try {
    await markNotificationRead(n.id)
    n.isRead = 1
    unreadStore.refresh()
  } catch (e) {
    // 错误提示已由请求拦截器统一处理
  }
}

async function readAll() {
  await markAllNotificationsRead()
  ElMessage.success('已全部标记为已读')
  await load()
}

onMounted(load)
</script>

<style scoped>
.noti {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.filters {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 15px;
  border-radius: var(--fc-r-pill);
  background: #fff;
  border: 1px solid var(--fc-line);
  font-size: 13px;
  color: var(--fc-ink-500);
  font-weight: 500;
  cursor: pointer;
  transition: all .15s ease;
  user-select: none;
}

.chip:hover {
  border-color: var(--fc-brand-100);
  color: var(--fc-brand-700);
  background: var(--fc-brand-50);
}

.chip.on {
  background: var(--fc-brand);
  border-color: var(--fc-brand);
  color: #fff;
  font-weight: 600;
  box-shadow: 0 2px 8px rgba(30, 158, 90, .26);
}

.chip b {
  font-size: 11px;
  font-weight: 700;
  opacity: .7;
}

.spacer {
  flex: 1;
}

.list {
  background: #fff;
  border: 1px solid var(--fc-line);
  border-radius: var(--fc-r-lg);
  box-shadow: var(--fc-sh-1);
  padding: 6px;
  overflow: hidden;
}

.msg {
  display: flex;
  gap: 14px;
  padding: 16px 18px;
  border-radius: var(--fc-r);
  border: 1px solid transparent;
  cursor: pointer;
  transition: background .15s ease;
}

.msg:hover {
  background: var(--fc-card-2);
}

.msg.unread {
  background: #F7FCF9;
  border-color: var(--fc-brand-50);
}

.msg .ic {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  flex: none;
  display: grid;
  place-items: center;
  font-size: 19px;
}

.tone-green { background: var(--fc-brand-50); }
.tone-orange { background: var(--fc-accent-50); }
.tone-blue { background: var(--fc-info-50); }
.tone-red { background: var(--fc-danger-50); }
.tone-gray { background: #F1F4F2; }
.tone-purple { background: var(--fc-purple-50); }

.msg .body {
  flex: 1;
  min-width: 0;
}

.r1 {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 5px;
}

.r1 h4 {
  margin: 0;
  font-size: 13.5px;
  font-weight: 650;
  color: var(--fc-ink-900);
}

.r1 .time {
  margin-left: auto;
  font-size: 11.5px;
  color: var(--fc-ink-300);
  flex: none;
}

.msg p {
  margin: 0;
  font-size: 13px;
  color: var(--fc-ink-500);
  line-height: 1.6;
}

.undot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--fc-accent);
  flex: none;
  margin-top: 6px;
}
</style>

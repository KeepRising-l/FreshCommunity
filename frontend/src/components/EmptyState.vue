<template>
  <div class="empty-state">
    <div class="icon-badge" aria-hidden="true">
      <el-icon><component :is="resolvedIcon" /></el-icon>
    </div>
    <h4 v-if="title">{{ title }}</h4>
    <p v-if="description">{{ description }}</p>
    <div v-if="$slots.action || actionText" class="action">
      <slot name="action">
        <el-button type="primary" @click="$emit('action')">{{ actionText }}</el-button>
      </slot>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const ICON_MAP = {
  '🔔': 'Bell',
  '🧺': 'ShoppingBag',
  '🧾': 'Document',
  '📦': 'Box',
  '🥬': 'Goods',
  '💬': 'ChatLineRound',
  '🔍': 'Search',
  '📊': 'DataAnalysis',
  '📭': 'CollectionTag'
}

const props = defineProps({
  icon: { type: String, default: 'CollectionTag' },
  title: { type: String, default: '这里还没有内容' },
  description: { type: String, default: '' },
  actionText: { type: String, default: '' }
})

defineEmits(['action'])

const resolvedIcon = computed(() => ICON_MAP[props.icon] || props.icon || 'CollectionTag')
</script>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 56px 24px;
}

.icon-badge {
  width: 56px;
  height: 56px;
  border-radius: 16px;
  display: grid;
  place-items: center;
  margin-bottom: 18px;
  background: linear-gradient(145deg, #FFFDF9, #F2EBE0);
  border: 1px solid var(--fc-line);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, .8), 0 4px 12px rgba(74, 53, 36, .05);
  color: var(--fc-brand);
  font-size: 24px;
}

h4 {
  margin: 0 0 8px;
  font-size: 15px;
  font-weight: 650;
  color: var(--fc-ink-800);
}

p {
  margin: 0;
  font-size: 13px;
  color: var(--fc-ink-400);
  line-height: 1.7;
  max-width: 320px;
}

.action {
  margin-top: 20px;
}
</style>

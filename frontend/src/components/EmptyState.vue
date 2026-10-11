<template>
  <div class="empty-state">
    <div class="icon">{{ icon }}</div>
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
defineProps({
  icon: { type: String, default: '📭' },
  title: { type: String, default: '这里还没有内容' },
  description: { type: String, default: '' },
  actionText: { type: String, default: '' }
})

defineEmits(['action'])
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

.icon {
  font-size: 52px;
  line-height: 1;
  margin-bottom: 18px;
  filter: grayscale(.12);
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

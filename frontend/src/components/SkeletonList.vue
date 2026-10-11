<template>
  <div class="skeleton-list">
    <div v-for="i in rows" :key="i" class="sk-item" :class="'v-' + variant">
      <div v-if="variant === 'card'" class="sk-thumb" />
      <div class="sk-lines">
        <div class="sk-line w-70" />
        <div class="sk-line w-45" />
        <div v-if="variant === 'card'" class="sk-line w-30" />
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  /** 骨架条数 */
  rows: { type: Number, default: 3 },
  /** card（带缩略图，用于团购/订单卡） | row（纯文本行，用于列表） */
  variant: { type: String, default: 'card' }
})
</script>

<style scoped>
.skeleton-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.sk-item {
  display: flex;
  gap: 14px;
  padding: 16px;
  background: var(--fc-card);
  border: 1px solid var(--fc-line);
  border-radius: var(--fc-r-lg);
}

.v-row {
  padding: 14px 4px;
  background: transparent;
  border: none;
  border-bottom: 1px solid var(--fc-line-soft);
  border-radius: 0;
}

.sk-thumb {
  width: 64px;
  height: 64px;
  border-radius: 14px;
  flex: none;
}

.sk-lines {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
  justify-content: center;
}

.sk-line {
  height: 12px;
  border-radius: 6px;
}

.w-70 { width: 70%; }
.w-45 { width: 45%; }
.w-30 { width: 30%; }

/* 微光扫过 */
.sk-thumb,
.sk-line {
  background: linear-gradient(90deg, #EDF1EE 25%, #F6F9F7 37%, #EDF1EE 63%);
  background-size: 400% 100%;
  animation: fc-shimmer 1.4s ease infinite;
}

@keyframes fc-shimmer {
  0% { background-position: 100% 50%; }
  100% { background-position: 0 50%; }
}

@media (prefers-reduced-motion: reduce) {
  .sk-thumb,
  .sk-line {
    animation: none;
  }
}
</style>

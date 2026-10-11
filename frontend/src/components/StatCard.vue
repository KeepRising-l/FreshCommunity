<template>
  <div class="stat-card">
    <div v-if="icon" class="ic" :class="'tone-' + tone">{{ icon }}</div>
    <div class="body">
      <div class="value fc-num">{{ value }}</div>
      <div class="label">{{ label }}</div>
    </div>
    <span v-if="trend" class="trend" :class="'dir-' + trendDir">{{ trend }}</span>
  </div>
</template>

<script setup>
defineProps({
  label: { type: String, required: true },
  value: { type: [String, Number], default: 0 },
  /** emoji 或短文本图标 */
  icon: { type: String, default: '' },
  /** green | orange | blue | purple | gray */
  tone: { type: String, default: 'green' },
  /** 右上角趋势文案，如 "↑ 18%" */
  trend: { type: String, default: '' },
  /** up | down | flat */
  trendDir: { type: String, default: 'up' }
})
</script>

<style scoped>
.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
  background: var(--fc-card);
  border: 1px solid var(--fc-line);
  border-radius: var(--fc-r-lg);
  box-shadow: var(--fc-sh-1);
  transition: box-shadow .18s ease, transform .18s ease;
}

.stat-card:hover {
  box-shadow: var(--fc-sh-2);
}

.ic {
  width: 44px;
  height: 44px;
  border-radius: 13px;
  display: grid;
  place-items: center;
  font-size: 21px;
  flex: none;
}

.tone-green { background: var(--fc-brand-50); }
.tone-orange { background: var(--fc-accent-50); }
.tone-blue { background: var(--fc-info-50); }
.tone-purple { background: var(--fc-purple-50); }
.tone-gray { background: #F1F4F2; }

.body {
  min-width: 0;
}

.value {
  font-size: 26px;
  font-weight: 750;
  color: var(--fc-ink-900);
  letter-spacing: -.6px;
  line-height: 1.15;
}

.label {
  font-size: 12.5px;
  color: var(--fc-ink-400);
  margin-top: 1px;
}

.trend {
  margin-left: auto;
  flex: none;
  font-size: 11.5px;
  font-weight: 650;
  padding: 3px 8px;
  border-radius: 6px;
}

.dir-up {
  background: var(--fc-brand-50);
  color: var(--fc-brand-700);
}

.dir-down {
  background: var(--fc-danger-50);
  color: var(--fc-danger);
}

.dir-flat {
  background: #F1F4F2;
  color: var(--fc-ink-500);
}
</style>

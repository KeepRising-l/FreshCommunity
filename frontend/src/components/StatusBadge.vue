<template>
  <span class="fc-badge" :class="'tone-' + resolved.tone">
    <i v-if="dot" class="dot" />
    {{ resolved.text }}
  </span>
</template>

<script setup>
import { computed } from 'vue'
import { orderStatus, groupBuyStatus, notificationType } from '../constants/status'

const props = defineProps({
  /** 状态来源：order | groupBuy | notification */
  kind: { type: String, default: 'order' },
  /** 后端返回的原始状态值 */
  status: { type: String, default: '' },
  /** 直接指定文案与配色（不传 kind/status 时使用） */
  text: { type: String, default: '' },
  tone: { type: String, default: 'gray' },
  /** 是否显示前置圆点 */
  dot: { type: Boolean, default: true }
})

const RESOLVERS = {
  order: orderStatus,
  groupBuy: groupBuyStatus,
  notification: notificationType
}

const resolved = computed(() => {
  if (props.text) return { text: props.text, tone: props.tone }
  const fn = RESOLVERS[props.kind] || orderStatus
  return fn(props.status)
})
</script>

<style scoped>
.fc-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 23px;
  padding: 0 10px;
  border-radius: 7px;
  font-size: 11.5px;
  font-weight: 600;
  white-space: nowrap;
  line-height: 1;
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  flex: none;
}

.tone-green {
  background: var(--fc-brand-50);
  color: var(--fc-brand-700);
}

.tone-orange {
  background: var(--fc-accent-50);
  color: var(--fc-accent-600);
}

.tone-blue {
  background: var(--fc-info-50);
  color: #2563EB;
}

.tone-gray {
  background: #F1F4F2;
  color: var(--fc-ink-500);
}

.tone-red {
  background: var(--fc-danger-50);
  color: var(--fc-danger);
}

.tone-yellow {
  background: var(--fc-warning-50);
  color: #B4740C;
}

.tone-purple {
  background: var(--fc-purple-50);
  color: var(--fc-purple);
}
</style>

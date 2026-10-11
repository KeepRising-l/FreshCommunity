<template>
  <article class="gb-card" :class="'v-' + layout" @click="$emit('click', group)">
    <div class="media" :class="visual.gradient">
      <span class="emoji">{{ visual.emoji }}</span>
      <span v-if="flag" class="flag" :class="flag.cls">{{ flag.text }}</span>
      <slot name="media-extra" />
    </div>

    <div class="body">
      <h4 class="title">{{ group.title }}</h4>

      <div v-if="pickupText" class="from">
        <el-icon><LocationInformation /></el-icon>
        <span>{{ pickupText }}</span>
      </div>

      <div v-if="hasPrice" class="price-line">
        <span class="fc-price price"><i>¥</i>{{ formatMoney(price) }}</span>
        <s v-if="originalPrice">{{ formatPrice(originalPrice) }}</s>
        <span v-if="saved" class="save-tag">省 {{ saved }} 元</span>
      </div>

      <div class="fc-progress">
        <i :class="{ 'is-hot': left.urgent }" :style="{ width: percent + '%' }" />
      </div>

      <div class="meta">
        <span>已参团 <b>{{ group.memberCount || 0 }}</b>/{{ group.targetCount || 0 }} 人</span>
        <span v-if="left.expired" class="cd">已截止</span>
        <span v-else class="cd" :class="{ urgent: left.urgent }">
          <el-icon><Clock /></el-icon>{{ left.text }}
        </span>
      </div>

      <el-button
        class="action"
        :type="actionBtnType"
        :disabled="actionDisabled"
        @click.stop="$emit('action', group)"
      >
        {{ resolvedActionText }}
      </el-button>
    </div>
  </article>
</template>

<script setup>
import { computed } from 'vue'
import { LocationInformation, Clock } from '@element-plus/icons-vue'
import { useNow } from '../composables/useNow'
import { productVisual } from '../utils/visual'
import { formatMoney, formatPrice, remaining, progressPercent, remainingMembers, fromNow } from '../utils/format'

const props = defineProps({
  /** GroupBuyListVO：{ id, title, targetCount, deadline, status, memberCount, leaderName, pickupPointName, pickupPointAddress, createdAt } */
  group: { type: Object, required: true },
  /** card（纵向，网格用） | row（横向，列表用） */
  layout: { type: String, default: 'card' },
  /** 以下为可选：后端团购列表暂未返回价格与图片，页面拿到时可传入 */
  price: { type: [Number, String], default: null },
  originalPrice: { type: [Number, String], default: null },
  /** 自定义按钮文案；不传则按状态推导 */
  actionText: { type: String, default: '' },
  actionDisabled: { type: Boolean, default: false },
  /** 是否展示"新开团"角标 */
  showNewFlag: { type: Boolean, default: true }
})

defineEmits(['action', 'click'])

const now = useNow()

const visual = computed(() => productVisual(props.group.title))
const percent = computed(() => progressPercent(props.group.memberCount, props.group.targetCount))
const left = computed(() => remaining(props.group.deadline, now.value))
const gap = computed(() => remainingMembers(props.group.memberCount, props.group.targetCount))

const pickupText = computed(() => {
  const name = props.group.pickupPointName
  const leader = props.group.leaderName
  if (name && leader) return `${name} · 团长 ${leader}`
  return name || (leader ? `团长 ${leader}` : '')
})

const hasPrice = computed(() => props.price !== null && props.price !== undefined && props.price !== '')

const saved = computed(() => {
  if (!hasPrice.value || !props.originalPrice) return ''
  const diff = Number(props.originalPrice) - Number(props.price)
  return diff > 0 ? diff.toFixed(0) : ''
})

const isSuccess = computed(() => props.group.status === 'SUCCESS')

/** 角标优先级：已成团 > 即将截止 > 新开团 */
const flag = computed(() => {
  if (isSuccess.value) return { text: '已成团', cls: 'flag-done' }
  if (left.value.expired) return { text: '已截止', cls: 'flag-done' }
  if (left.value.urgent) return { text: '即将截止', cls: 'flag-hot' }

  if (props.showNewFlag && props.group.createdAt) {
    const created = new Date(props.group.createdAt).getTime()
    if (Number.isFinite(created) && now.value - created < 24 * 3600 * 1000) {
      return { text: '新开团', cls: 'flag-new' }
    }
  }
  return null
})

const resolvedActionText = computed(() => {
  if (props.actionText) return props.actionText
  if (isSuccess.value) return '已成团 · 去提货'
  if (left.value.expired) return '已截止'
  if (gap.value > 0) return `立即参团 · 还差 ${gap.value} 人`
  return '立即参团'
})

const actionBtnType = computed(() => (left.value.urgent && !isSuccess.value ? 'warning' : 'primary'))

defineExpose({ left })
</script>

<style scoped>
.gb-card {
  display: flex;
  flex-direction: column;
  background: var(--fc-card);
  border: 1px solid var(--fc-line);
  border-radius: var(--fc-r-lg);
  box-shadow: var(--fc-sh-1);
  overflow: hidden;
  transition: transform .2s ease, box-shadow .2s ease, border-color .2s ease;
  cursor: pointer;
}

.gb-card:hover {
  transform: translateY(-3px);
  box-shadow: var(--fc-sh-2);
  border-color: var(--fc-brand-100);
}

/* ---- 纵向卡片 ---- */
.v-card .media {
  height: 132px;
  position: relative;
  display: grid;
  place-items: center;
}

.v-card .emoji {
  font-size: 52px;
}

/* ---- 横向行 ---- */
.v-row {
  flex-direction: row;
  align-items: stretch;
}

.v-row .media {
  width: 108px;
  flex: none;
  display: grid;
  place-items: center;
  position: relative;
}

.v-row .emoji {
  font-size: 40px;
}

.v-row .body {
  flex: 1;
  min-width: 0;
}

.flag {
  position: absolute;
  left: 12px;
  top: 12px;
  font-size: 11px;
  font-weight: 700;
  padding: 4px 10px;
  border-radius: var(--fc-r-pill);
  color: #fff;
  line-height: 1.4;
}

.flag-hot {
  background: linear-gradient(90deg, #FF8A3D, #FF5E1A);
  box-shadow: 0 3px 10px rgba(255, 94, 26, .34);
}

.flag-new {
  background: linear-gradient(90deg, #38BDF8, #0EA5E9);
}

.flag-done {
  background: rgba(15, 26, 20, .62);
}

.body {
  padding: 15px 16px 16px;
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
}

.title {
  margin: 0 0 6px;
  font-size: 14.5px;
  font-weight: 650;
  color: var(--fc-ink-900);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.from {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--fc-ink-400);
  font-size: 12px;
  margin-bottom: 11px;
  min-width: 0;
}

.from span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.price-line {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 13px;
}

.price {
  font-size: 20px;
}

.price-line s {
  color: var(--fc-ink-300);
  font-size: 12.5px;
}

.save-tag {
  font-size: 11px;
  font-weight: 700;
  color: var(--fc-accent-600);
  background: var(--fc-accent-50);
  padding: 2px 7px;
  border-radius: 5px;
}

.meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin: 8px 0 14px;
  font-size: 12px;
  color: var(--fc-ink-400);
}

.meta b {
  color: var(--fc-brand-700);
  font-weight: 700;
}

.cd {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--fc-accent-600);
  font-weight: 600;
  font-size: 11.5px;
  white-space: nowrap;
}

.cd.urgent {
  color: var(--fc-danger);
}

.action {
  margin-top: auto;
  width: 100%;
}
</style>

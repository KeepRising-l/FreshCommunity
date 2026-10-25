<template>
  <div class="console">
    <header class="head">
      <h2>核销台</h2>
      <p class="sub">输入买家出示的 6 位取货码，确认后订单即标记为已完成</p>
    </header>

    <section class="card panel">
      <label class="field-label" for="pickup-code">取货码</label>
      <input
        id="pickup-code"
        ref="inputRef"
        v-model="code"
        class="code-input"
        inputmode="numeric"
        autocomplete="one-time-code"
        maxlength="6"
        placeholder="000000"
        @input="onInput"
        @keyup.enter="verify"
      />
      <p class="input-hint">仅支持数字，共 6 位</p>

      <el-button
        type="primary"
        class="verify-btn"
        :loading="submitting"
        :disabled="code.length !== 6"
        @click="verify"
      >
        确认核销
      </el-button>

      <div v-if="result" class="result" :class="result.ok ? 'is-ok' : 'is-bad'">
        <span class="result-icon">{{ result.ok ? '✅' : '⚠️' }}</span>
        <div>
          <b>{{ result.title }}</b>
          <p>{{ result.detail }}</p>
        </div>
      </div>

      <ul class="safety">
        <li>只能核销你本人发起的团购订单，其他团长的取货码会被拒绝</li>
        <li>每笔订单只能核销一次，重复提交会提示状态不允许</li>
        <li>输入错误不会清空已输内容，可直接修改后重试</li>
      </ul>
    </section>

    <section v-loading="loadingRecords" class="card records">
      <div class="records-head">
        <h3>最近核销</h3>
        <span class="fc-muted">最近 10 笔</span>
      </div>

      <EmptyState
        v-if="!records.length"
        icon="🧾"
        title="还没有核销记录"
        description="核销成功后，这里会按时间倒序列出最近 10 笔。"
      />

      <ul v-else class="record-list">
        <li v-for="r in records" :key="r.orderNo + r.verifiedAt">
          <div class="r-main">
            <b>{{ r.groupBuyTitle }}</b>
            <span class="fc-num">{{ r.orderNo }}</span>
          </div>
          <div class="r-meta">
            <span class="fc-num">{{ r.quantity }} 件 · ¥{{ money(r.amount) }}</span>
            <span class="fc-num">{{ formatTime(r.verifiedAt) }}</span>
          </div>
        </li>
      </ul>
    </section>
  </div>
</template>

<script setup>
import { nextTick, onMounted, ref } from 'vue'
import { verifyPickupCode, listVerifyHistory } from '../api/order'
import { playSuccessSound, playErrorSound } from '../utils/sound'
import EmptyState from '../components/EmptyState.vue'

const inputRef = ref(null)
const code = ref('')
const submitting = ref(false)
const result = ref(null)
const records = ref([])
const loadingRecords = ref(true)

const money = (v) =>
  Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

function formatTime(v) {
  return v ? String(v).replace('T', ' ').slice(0, 16) : '—'
}

function onInput() {
  code.value = code.value.replace(/\D/g, '').slice(0, 6)
}

async function verify() {
  if (code.value.length !== 6 || submitting.value) return
  const submitted = code.value
  submitting.value = true
  result.value = null
  try {
    await verifyPickupCode(submitted, { skipErrorToast: true })
    result.value = {
      ok: true,
      title: '核销成功',
      detail: `取货码 ${submitted} 对应订单已完成，买家会收到提货完成通知。`
    }
    playSuccessSound()
    code.value = ''
    await loadHistory()
  } catch (e) {
    result.value = {
      ok: false,
      title: '核销失败',
      detail: e?.response?.data?.msg || e?.message || '核销失败，请重试'
    }
    playErrorSound()
  } finally {
    submitting.value = false
    await nextTick()
    inputRef.value?.focus()
  }
}

async function loadHistory() {
  loadingRecords.value = true
  try {
    const res = await listVerifyHistory()
    records.value = res.data || []
  } finally {
    loadingRecords.value = false
  }
}

onMounted(() => {
  loadHistory()
  inputRef.value?.focus()
})
</script>

<style scoped>
.console {
  width: 100%;
  max-width: 560px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.head {
  text-align: center;
  padding: 4px 0 2px;
}

.head h2 {
  margin: 0 0 6px;
  font-size: 20px;
  font-weight: 750;
  letter-spacing: -0.4px;
  color: var(--fc-ink-900);
}

.sub {
  margin: 0;
  font-size: 12.5px;
  color: var(--fc-ink-400);
}

.card {
  background: var(--fc-card);
  border: 1px solid var(--fc-line);
  border-radius: var(--fc-r-lg);
  box-shadow: var(--fc-sh-1);
}

.panel {
  padding: 20px;
}

.field-label {
  display: block;
  font-size: 12.5px;
  font-weight: 650;
  color: var(--fc-ink-700);
  margin-bottom: 8px;
}

.code-input {
  width: 100%;
  height: 66px;
  border-radius: var(--fc-r-lg);
  border: 2px solid var(--fc-line);
  background: var(--fc-card-2);
  text-align: center;
  font-family: var(--fc-font);
  font-size: 30px;
  font-weight: 650;
  font-variant-numeric: tabular-nums;
  letter-spacing: 14px;
  text-indent: 14px;
  color: var(--fc-ink-900);
  outline: none;
  transition: border-color 0.15s ease, box-shadow 0.15s ease, background 0.15s ease;
}

.code-input:focus {
  border-color: var(--fc-brand);
  background: #fff;
  box-shadow: 0 0 0 4px rgba(30, 158, 90, 0.12);
}

.code-input::placeholder {
  color: var(--fc-ink-300);
  font-weight: 500;
}

.input-hint {
  margin: 7px 0 14px;
  font-size: 11.5px;
  color: var(--fc-ink-400);
}

.verify-btn {
  width: 100%;
  height: 50px;
  font-size: 16px;
  letter-spacing: 0.5px;
}

.result {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-top: 14px;
  padding: 12px 14px;
  border-radius: var(--fc-r);
}

.result.is-ok {
  background: var(--fc-brand-50);
  border: 1px solid var(--fc-brand-100);
}

.result.is-bad {
  background: var(--fc-danger-50);
  border: 1px solid #F7D4D5;
}

.result-icon {
  font-size: 18px;
  line-height: 1.35;
  flex: none;
}

.result b {
  display: block;
  font-size: 13.5px;
  font-weight: 700;
  color: var(--fc-ink-900);
}

.result p {
  margin: 2px 0 0;
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--fc-ink-500);
  word-break: break-all;
}

.safety {
  margin: 16px 0 0;
  padding: 14px 0 0;
  border-top: 1px dashed var(--fc-line);
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.safety li {
  position: relative;
  padding-left: 16px;
  font-size: 11.5px;
  line-height: 1.6;
  color: var(--fc-ink-400);
}

.safety li::before {
  content: "·";
  position: absolute;
  left: 4px;
  top: -1px;
  font-weight: 700;
  color: var(--fc-ink-300);
}

.records {
  padding: 18px 20px 8px;
}

.records-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 6px;
}

.records-head h3 {
  margin: 0;
  font-size: 14.5px;
  font-weight: 700;
  color: var(--fc-ink-900);
}

.record-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.record-list li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 11px 0;
  border-bottom: 1px solid var(--fc-line-soft);
}

.record-list li:last-child {
  border-bottom: none;
}

.r-main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.r-main b {
  font-size: 13px;
  font-weight: 650;
  color: var(--fc-ink-800);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.r-main span {
  font-size: 11px;
  color: var(--fc-ink-400);
}

.r-meta {
  flex: none;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
  font-size: 11.5px;
  color: var(--fc-ink-500);
}

@media (max-width: 560px) {
  .code-input {
    height: 60px;
    font-size: 26px;
    letter-spacing: 10px;
    text-indent: 10px;
  }
}
</style>

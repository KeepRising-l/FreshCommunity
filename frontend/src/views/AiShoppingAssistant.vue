<template>
  <div class="ai-page">
    <section class="hero">
      <div class="hero-copy">
        <span class="eyebrow">AI 导购 · 需求订阅</span>
        <h1>告诉我想买什么，<em>有合适的团就提醒你</em></h1>
        <p>用一句话描述商品和预算。系统会先查看正在进行的团购，也会持续留意新开团。</p>
      </div>
      <div class="hero-mark" aria-hidden="true">🥬</div>
    </section>

    <section class="compose card">
      <div class="section-heading">
        <div>
          <span class="step">01</span>
          <h2>写下你的需求</h2>
        </div>
        <span class="hint">例如：想要 10 元以内的苹果，有货叫我</span>
      </div>

      <el-input
        v-model="text"
        type="textarea"
        :rows="3"
        maxlength="255"
        show-word-limit
        resize="none"
        placeholder="说说你想买什么，价格有没有上限……"
        @input="clearPreview"
        @keydown.ctrl.enter.prevent="parseDemand"
      />

      <div class="compose-footer">
        <div class="examples">
          <span>试试：</span>
          <button v-for="example in examples" :key="example" type="button" @click="selectExample(example)">
            {{ example }}
          </button>
        </div>
        <el-button type="primary" :loading="previewing" :disabled="!text.trim()" @click="parseDemand">
          解析需求
        </el-button>
      </div>
    </section>

    <section v-if="preview" class="result card preview-card" aria-live="polite">
      <div class="result-icon">?</div>
      <div class="result-copy">
        <h3>请确认解析条件</h3>
        <p>“{{ preview.sourceText }}”会按以下条件持续查找团购：</p>
        <div class="result-tags">
          <el-tag round effect="light">{{ preview.keyword }}</el-tag>
          <el-tag round effect="light" type="warning">
            {{ preview.maxPrice == null ? '不限价格' : `价格不高于 ¥${preview.maxPrice}` }}
          </el-tag>
        </div>
      </div>
      <div class="result-actions">
        <el-button text @click="clearPreview">重新描述</el-button>
        <el-button type="primary" :loading="confirming" @click="confirmSubscription">
          确认并订阅
        </el-button>
      </div>
    </section>

    <section v-if="lastResult" class="result card" aria-live="polite">
      <div class="result-icon">✓</div>
      <div class="result-copy">
        <h3>需求已记下</h3>
        <p>“{{ lastResult.sourceText }}”已整理为以下条件：</p>
        <div class="result-tags">
          <el-tag round effect="light">{{ lastResult.keyword }}</el-tag>
          <el-tag round effect="light" type="warning">
            {{ lastResult.maxPrice == null ? '不限价格' : `价格不高于 ¥${lastResult.maxPrice}` }}
          </el-tag>
        </div>
        <p class="match-note">
          {{ lastResult.matchedCount > 0
            ? `已找到 ${lastResult.matchedCount} 场正在进行的团购，提醒已发送到消息中心。`
            : '目前没有符合条件的团购；有新团时会通过消息中心提醒你。' }}
        </p>
      </div>
      <router-link to="/notifications" class="message-link">查看消息</router-link>
    </section>

    <section class="subscriptions">
      <div class="section-heading list-heading">
        <div>
          <span class="step">02</span>
          <h2>我的关注</h2>
          <span class="count">{{ subscriptions.length }}</span>
        </div>
        <span class="hint">取消后将不再收到对应商品的提醒</span>
      </div>

      <SkeletonList v-if="loading" :rows="2" />
      <EmptyState
        v-else-if="subscriptions.length === 0"
        icon="🔔"
        title="还没有关注的商品"
        description="提交上方的需求后，符合条件的新团会出现在这里。"
      />
      <div v-else class="subscription-list">
        <article v-for="item in subscriptions" :key="item.id" class="subscription card">
          <div class="subscription-icon">{{ item.keyword.slice(0, 1) || '果' }}</div>
          <div class="subscription-copy">
            <div class="subscription-title">
              <strong>{{ item.keyword }}</strong>
              <el-tag size="small" round effect="light" type="warning">
                {{ item.maxPrice == null ? '不限价格' : `≤ ¥${item.maxPrice}` }}
              </el-tag>
            </div>
            <p>{{ item.sourceText }}</p>
            <small>关注于 {{ formatDate(item.createdAt) }}</small>
          </div>
          <el-button text type="danger" :loading="cancellingId === item.id" @click="cancel(item)">
            取消关注
          </el-button>
        </article>
      </div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import EmptyState from '../components/EmptyState.vue'
import SkeletonList from '../components/SkeletonList.vue'
import {
  cancelSubscription,
  createSubscription,
  listSubscriptions,
  previewSubscription
} from '../api/subscription'

const examples = ['10 元以内的苹果', '想买便宜的草莓']
const text = ref('')
const subscriptions = ref([])
const preview = ref(null)
const lastResult = ref(null)
const loading = ref(true)
const previewing = ref(false)
const confirming = ref(false)
const cancellingId = ref(null)

async function loadSubscriptions() {
  loading.value = true
  try {
    const result = await listSubscriptions()
    subscriptions.value = result.data || []
  } catch {
    subscriptions.value = []
  } finally {
    loading.value = false
  }
}

async function parseDemand() {
  const demand = text.value.trim()
  if (!demand || previewing.value) return

  previewing.value = true
  lastResult.value = null
  try {
    const result = await previewSubscription(demand)
    preview.value = { ...result.data, sourceText: demand }
  } catch {
    // 请求拦截器会显示后端返回的错误信息。
  } finally {
    previewing.value = false
  }
}

async function confirmSubscription() {
  if (!preview.value || confirming.value) return

  confirming.value = true
  try {
    const { sourceText, keyword, maxPrice } = preview.value
    const result = await createSubscription(sourceText, { keyword, maxPrice })
    lastResult.value = result.data
    preview.value = null
    text.value = ''
    ElMessage.success('订阅已创建')
    await loadSubscriptions()
  } catch {
    // 请求拦截器会显示后端返回的错误信息。
  } finally {
    confirming.value = false
  }
}

function clearPreview() {
  preview.value = null
  lastResult.value = null
}

function selectExample(example) {
  text.value = example
  clearPreview()
}

async function cancel(item) {
  try {
    await ElMessageBox.confirm(
      `取消后将不再收到“${item.keyword}”的团购提醒。`,
      '取消关注',
      { confirmButtonText: '确认取消', cancelButtonText: '再想想', type: 'warning' }
    )
    cancellingId.value = item.id
    await cancelSubscription(item.id)
    subscriptions.value = subscriptions.value.filter((entry) => entry.id !== item.id)
    ElMessage.success('已取消关注')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      // 请求拦截器会显示后端返回的错误信息。
    }
  } finally {
    cancellingId.value = null
  }
}

function formatDate(value) {
  if (!value) return '刚刚'
  return String(value).replace('T', ' ').slice(0, 16)
}

onMounted(loadSubscriptions)
</script>

<style scoped>
.ai-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
  max-width: 1040px;
  margin: 0 auto;
}

.hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  min-height: 190px;
  padding: 30px 36px;
  overflow: hidden;
  border: 1px solid #d8eee0;
  border-radius: var(--fc-r-xl);
  background: linear-gradient(115deg, #effaf3 0%, #f8fcf8 62%, #fff7ed 100%);
}

.hero-copy { max-width: 720px; }
.eyebrow { color: var(--fc-brand-700); font-size: 12px; font-weight: 700; letter-spacing: .8px; }
.hero h1 { margin: 10px 0 8px; color: var(--fc-ink-900); font-size: clamp(22px, 3vw, 30px); line-height: 1.35; }
.hero h1 em { color: var(--fc-brand-600); font-style: normal; }
.hero p { margin: 0; color: var(--fc-ink-500); font-size: 14px; line-height: 1.7; }
.hero-mark { display: grid; place-items: center; width: 100px; height: 100px; flex: none; border-radius: 30px; background: rgba(255,255,255,.72); font-size: 50px; box-shadow: var(--fc-sh-1); }

.card { border: 1px solid var(--fc-line); border-radius: var(--fc-r-lg); background: #fff; box-shadow: var(--fc-sh-1); }
.compose { padding: 22px 24px 20px; }
.section-heading, .section-heading > div { display: flex; align-items: center; }
.section-heading { justify-content: space-between; gap: 12px; margin-bottom: 16px; }
.section-heading > div { gap: 10px; min-width: 0; }
.section-heading h2 { margin: 0; color: var(--fc-ink-900); font-size: 17px; font-weight: 650; }
.step { display: grid; place-items: center; width: 25px; height: 25px; flex: none; border-radius: 9px; background: var(--fc-brand-50); color: var(--fc-brand-700); font-size: 11px; font-weight: 700; }
.hint { color: var(--fc-ink-400); font-size: 12px; }
.compose :deep(.el-textarea__inner) { min-height: 94px !important; border-color: var(--fc-line); border-radius: var(--fc-r); padding: 13px 15px; color: var(--fc-ink-800); font-size: 14px; line-height: 1.7; box-shadow: none; }
.compose :deep(.el-textarea__inner:focus) { border-color: var(--fc-brand); }
.compose-footer { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-top: 14px; }
.examples { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; color: var(--fc-ink-400); font-size: 12px; }
.examples button { border: 1px solid var(--fc-line); border-radius: var(--fc-r-pill); padding: 6px 10px; background: #fff; color: var(--fc-ink-500); cursor: pointer; font: inherit; transition: all .15s ease; }
.examples button:hover { border-color: var(--fc-brand-100); background: var(--fc-brand-50); color: var(--fc-brand-700); }
.compose-footer :deep(.el-button) { min-height: 38px; padding-inline: 18px; }

.result { display: flex; align-items: flex-start; gap: 14px; padding: 18px 20px; border-color: #d8eee0; background: #fbfefc; }
.result-icon { display: grid; place-items: center; width: 34px; height: 34px; flex: none; border-radius: 50%; background: var(--fc-brand-50); color: var(--fc-brand-700); font-size: 18px; font-weight: 700; }
.result-copy { flex: 1; min-width: 0; }
.result h3 { margin: 1px 0 5px; color: var(--fc-ink-900); font-size: 15px; }
.result p { margin: 0; color: var(--fc-ink-500); font-size: 13px; line-height: 1.6; overflow-wrap: anywhere; }
.result-tags { display: flex; gap: 8px; margin: 10px 0; }
.result .match-note { color: var(--fc-brand-700); }
.message-link { flex: none; color: var(--fc-brand-700); font-size: 13px; text-decoration: none; }
.message-link:hover { text-decoration: underline; }
.result-actions { display: flex; align-items: center; gap: 6px; flex: none; }

.subscriptions { display: flex; flex-direction: column; gap: 12px; }
.list-heading { margin: 0 2px 2px; }
.count { display: grid; place-items: center; min-width: 21px; height: 21px; padding: 0 6px; border-radius: var(--fc-r-pill); background: #fff; color: var(--fc-ink-500); font-size: 11px; }
.subscription-list { display: flex; flex-direction: column; gap: 10px; }
.subscription { display: flex; align-items: center; gap: 14px; padding: 16px 18px; }
.subscription-icon { display: grid; place-items: center; width: 42px; height: 42px; flex: none; border-radius: 14px; background: linear-gradient(145deg, #eaf7f0, #fff3e8); color: var(--fc-brand-700); font-size: 19px; font-weight: 700; }
.subscription-copy { flex: 1; min-width: 0; }
.subscription-title { display: flex; align-items: center; gap: 9px; }
.subscription-title strong { color: var(--fc-ink-900); font-size: 15px; }
.subscription-copy p { margin: 5px 0 3px; color: var(--fc-ink-500); font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.subscription-copy small { color: var(--fc-ink-400); font-size: 11px; }
.subscription :deep(.el-button) { flex: none; }

@media (max-width: 680px) {
  .ai-page { gap: 14px; }
  .hero { min-height: unset; padding: 22px 20px; }
  .hero-mark { width: 62px; height: 62px; border-radius: 20px; font-size: 31px; }
  .hero p { font-size: 13px; }
  .compose { padding: 18px 16px; }
  .section-heading { align-items: flex-start; flex-direction: column; }
  .list-heading { flex-direction: row; align-items: center; }
  .compose-footer { align-items: stretch; flex-direction: column; }
  .compose-footer :deep(.el-button) { width: 100%; }
  .result { flex-wrap: wrap; }
  .message-link { margin-left: 48px; }
  .result-actions { width: 100%; justify-content: flex-end; }
  .subscription { align-items: flex-start; padding: 14px; }
  .subscription-icon { width: 36px; height: 36px; }
  .subscription :deep(.el-button) { padding-inline: 5px; }
}
</style>

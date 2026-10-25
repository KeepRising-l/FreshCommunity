<template>
  <div class="ai-page">
    <section class="hero">
      <div class="hero-copy">
        <span class="eyebrow">AI 导购</span>
        <h1>想要什么<em>问一句</em>，AI 帮你挑好</h1>
        <p>描述你的需求，AI 会对比所有在售商品和正在进行的团购，直接推荐最合适的，并说明理由。</p>
      </div>
      <div class="hero-mark" aria-hidden="true">🤖</div>
    </section>

    <!-- 01 即时推荐 -->
    <section class="compose card">
      <div class="section-heading">
        <div>
          <span class="step">01</span>
          <h2>AI 帮我挑</h2>
        </div>
        <span class="hint">例如：帮我找性价比比较高的苹果</span>
      </div>

      <el-input
        v-model="askText"
        type="textarea"
        :rows="3"
        maxlength="255"
        show-word-limit
        resize="none"
        placeholder="说说你想买什么、在意什么（价格 / 性价比 / 新鲜度）……"
        @keydown.ctrl.enter.prevent="ask"
      />

      <div class="compose-footer">
        <div class="examples">
          <span>试试：</span>
          <button v-for="example in askExamples" :key="example" type="button" @click="selectAskExample(example)">
            {{ example }}
          </button>
        </div>
        <el-button type="primary" :loading="asking" :disabled="!askText.trim()" @click="ask">
          {{ asking ? '正在对比在售商品…' : '问 AI' }}
        </el-button>
      </div>
    </section>

    <!-- 推荐结果 -->
    <section v-if="answer || recommendError" class="answer card" aria-live="polite">
      <div class="answer-icon">{{ recommendError ? '⚠️' : '🤖' }}</div>
      <div class="answer-body">
        <h3>{{ recommendError ? '这次没答上来' : 'AI 的推荐' }}</h3>
        <p v-if="recommendError" class="answer-text">{{ recommendError }}</p>
        <p v-else class="answer-text">{{ answer }}</p>

        <div v-if="answerItems.length" class="answer-items">
          <article
            v-for="item in answerItems"
            :key="item.productId"
            class="rec-card"
            @click="goItem(item)"
          >
            <div class="rec-thumb">
              <img v-if="item.imageUrl" :src="item.imageUrl" :alt="item.name" loading="lazy" />
              <span v-else>{{ (item.name || '🧺').slice(0, 1) }}</span>
            </div>
            <div class="rec-info">
              <b>{{ item.name }}</b>
              <span v-if="item.groupTitle" class="rec-group">{{ item.groupTitle }}</span>
              <div class="rec-price">
                <em class="fc-price"><i>¥</i>{{ formatMoney(item.price) }}</em>
                <s v-if="item.originalPrice && item.originalPrice > item.price">
                  ¥{{ formatMoney(item.originalPrice) }}
                </s>
              </div>
              <div v-if="item.insightTags && item.insightTags.length" class="rec-tags">
                <span v-for="tag in item.insightTags.slice(0, 3)" :key="tag">{{ tag }}</span>
              </div>
            </div>
            <div class="rec-side">
              <span class="rec-go">{{ item.groupBuyId ? '去看团 ›' : '看详情 ›' }}</span>
              <el-button
                v-if="item.groupBuyId"
                type="primary"
                size="small"
                class="rec-order-btn"
                @click.stop="openOrder(item)"
              >
                立即参团
              </el-button>
            </div>
          </article>
        </div>

        <div v-if="!recommendError && answerItems.length" class="answer-foot">
          <el-button size="small" @click="subscribeFromAnswer">没抢到？订阅这个需求，有货提醒我</el-button>
        </div>
      </div>
    </section>

    <!-- 参团下单弹窗：从推荐卡片直接成交 -->
    <el-dialog v-model="orderVisible" title="参团下单" width="420px">
      <div v-if="orderTarget" class="order-body">
        <div class="order-prod">
          <div class="order-thumb">
            <img v-if="orderTarget.imageUrl" :src="orderTarget.imageUrl" :alt="orderTarget.name" />
            <span v-else>{{ (orderTarget.name || '🧺').slice(0, 1) }}</span>
          </div>
          <div class="order-info">
            <b>{{ orderTarget.name }}</b>
            <span class="fc-muted">{{ orderTarget.groupTitle }}</span>
            <div class="fc-price" style="font-size: 18px">
              <i>¥</i>{{ formatMoney(orderTarget.price) }}
              <s v-if="orderTarget.originalPrice && orderTarget.originalPrice > orderTarget.price"
                 style="font-size: 11px; color: var(--fc-ink-300); font-weight: 400; margin-left: 6px;">
                ¥{{ formatMoney(orderTarget.originalPrice) }}
              </s>
            </div>
          </div>
        </div>

        <div class="order-row">
          <span class="order-label">数量</span>
          <el-input-number v-model="orderQuantity" :min="1" :max="99" />
        </div>
        <div class="order-row">
          <span class="order-label">提货点</span>
          <span class="order-value">{{ orderTarget.pickupPointName || '以团购详情为准' }}</span>
        </div>
        <div class="order-row total">
          <span class="order-label">应付</span>
          <span class="fc-price" style="font-size: 20px">
            <i>¥</i>{{ formatMoney(orderTarget.price * orderQuantity) }}
          </span>
        </div>
        <div class="order-note">
          🛡️ 下单后进入「待支付」，30 分钟内完成支付即参团；截止前未成团自动全额退款。
        </div>
      </div>
      <template #footer>
        <el-button @click="orderVisible = false">再想想</el-button>
        <el-button type="primary" :loading="ordering" @click="submitOrder">确认下单</el-button>
      </template>
    </el-dialog>

    <!-- 02 订阅提醒 -->
    <section class="compose card">
      <div class="section-heading">
        <div>
          <span class="step">02</span>
          <h2>订阅提醒</h2>
        </div>
        <span class="hint">现在没有合适的？留下需求，新开团自动叫你</span>
      </div>

      <el-input
        v-model="text"
        type="textarea"
        :rows="2"
        maxlength="255"
        show-word-limit
        resize="none"
        placeholder="例如：想要 10 元以内的苹果，有货叫我"
        @input="clearPreview"
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
          <span class="step">03</span>
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
import { useRouter } from 'vue-router'
import EmptyState from '../components/EmptyState.vue'
import SkeletonList from '../components/SkeletonList.vue'
import { aiRecommend } from '../api/ai'
import { createOrder, payOrder } from '../api/order'
import {
  cancelSubscription,
  createSubscription,
  listSubscriptions,
  previewSubscription
} from '../api/subscription'
import { formatMoney } from '../utils/format'

const router = useRouter()

const askExamples = ['帮我找性价比比较高的苹果', '20 元以内有什么水果', '想买新鲜的水产']
const examples = ['10 元以内的苹果', '想买便宜的草莓']

// 01 即时推荐
const askText = ref('')
const asking = ref(false)
const answer = ref('')
const answerItems = ref([])
const recommendError = ref('')

// 推荐卡片直接参团下单
const orderVisible = ref(false)
const orderTarget = ref(null)
const orderQuantity = ref(1)
const ordering = ref(false)

// 02 订阅
const text = ref('')
const subscriptions = ref([])
const preview = ref(null)
const lastResult = ref(null)
const loading = ref(true)
const previewing = ref(false)
const confirming = ref(false)
const cancellingId = ref(null)

async function ask() {
  const demand = askText.value.trim()
  if (!demand || asking.value) return

  asking.value = true
  answer.value = ''
  answerItems.value = []
  recommendError.value = ''
  try {
    const result = await aiRecommend({ text: demand })
    answer.value = result.data?.reply || ''
    answerItems.value = result.data?.items || []
  } catch (e) {
    // 拦截器已 toast；页面内再留一条可读的失败说明（LLM 不可用是预期场景）
    recommendError.value = e?.message || 'AI 服务暂时不可用，请稍后重试'
  } finally {
    asking.value = false
  }
}

function selectAskExample(example) {
  askText.value = example
  answer.value = ''
  answerItems.value = []
  recommendError.value = ''
}

function goItem(item) {
  router.push(item.groupBuyId ? '/user/groups/' + item.groupBuyId : '/user/products/' + item.productId)
}

/** 推荐卡片直接参团：确认数量与提货点后按拼团价下单 */
function openOrder(item) {
  orderTarget.value = item
  orderQuantity.value = 1
  orderVisible.value = true
}

async function submitOrder() {
  const item = orderTarget.value
  if (!item || !item.groupBuyId || ordering.value) return
  ordering.value = true
  try {
    await createOrder(item.groupBuyId, orderQuantity.value)
    orderVisible.value = false
    ElMessageBox.confirm(
      `已下单「${item.name} × ${orderQuantity.value}」，订单进入待支付，30 分钟内支付即参团。`,
      '下单成功',
      { confirmButtonText: '去支付', cancelButtonText: '稍后去订单列表', type: 'success' }
    )
      .then(async () => {
        // 支付需要订单 id：从我的订单取最新一笔该团的待支付单
        const { listMyOrders } = await import('../api/order')
        const res = await listMyOrders()
        const mine = (res.data || []).find(
          (o) => o.groupBuyId === item.groupBuyId && o.status === 'PENDING_PAYMENT'
        )
        if (!mine) {
          ElMessage.warning('未找到待支付订单，请到我的订单查看')
          return
        }
        await payOrder(mine.id)
        ElMessage.success('支付成功！成团后凭取货码到提货点自提')
        router.push('/user/orders')
      })
      .catch((reason) => {
        // 取消/关闭弹窗不属于错误；支付失败由拦截器提示
        if (reason !== 'cancel' && reason !== 'close') {
          router.push('/user/orders')
        }
      })
  } catch (e) {
    // 错误提示已由请求拦截器统一处理（如库存不足）
  } finally {
    ordering.value = false
  }
}

/** 把当前问句转为订阅（衔接 02 流程：填入并解析） */
function subscribeFromAnswer() {
  text.value = askText.value.trim()
  clearPreview()
  parseDemand()
}

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

/* ---------- AI 回答区 ---------- */
.answer { display: flex; align-items: flex-start; gap: 14px; padding: 20px 22px; border-color: #d8eee0; background: #fbfefc; }
.answer-icon { display: grid; place-items: center; width: 36px; height: 36px; flex: none; border-radius: 50%; background: var(--fc-brand-50); font-size: 19px; }
.answer-body { flex: 1; min-width: 0; }
.answer h3 { margin: 1px 0 7px; color: var(--fc-ink-900); font-size: 15px; }
.answer-text { margin: 0 !important; white-space: pre-line; color: var(--fc-ink-800) !important; font-size: 13.5px !important; line-height: 1.9 !important; }

.answer-items { display: grid; grid-template-columns: repeat(auto-fill, minmax(230px, 1fr)); gap: 12px; margin-top: 14px; }
.rec-card { display: flex; gap: 11px; padding: 11px; border: 1px solid var(--fc-line); border-radius: var(--fc-r); background: #fff; cursor: pointer; transition: all .15s ease; }
.rec-card:hover { border-color: var(--fc-brand-100); box-shadow: var(--fc-sh-1); transform: translateY(-1px); }
.rec-thumb { width: 54px; height: 54px; border-radius: 12px; flex: none; display: grid; place-items: center; overflow: hidden; background: var(--fc-brand-50); color: var(--fc-brand-700); font-size: 20px; font-weight: 700; }
.rec-thumb img { width: 100%; height: 100%; object-fit: cover; display: block; }
.rec-info { flex: 1; min-width: 0; }
.rec-info b { display: block; font-size: 13.5px; color: var(--fc-ink-900); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.rec-group { display: block; font-size: 11px; color: var(--fc-ink-400); margin: 2px 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.rec-price { display: flex; align-items: baseline; gap: 7px; }
.rec-price em { font-style: normal; font-weight: 750; color: var(--fc-accent-600); font-size: 15px; }
.rec-price em i { font-style: normal; font-size: .7em; margin-right: 1px; }
.rec-price s { color: var(--fc-ink-300); font-size: 11px; }
.rec-tags { display: flex; gap: 5px; margin-top: 5px; flex-wrap: wrap; }
.rec-tags span { font-size: 10.5px; color: var(--fc-brand-700); background: var(--fc-brand-50); border-radius: var(--fc-r-pill); padding: 1px 8px; }
.rec-go { align-self: center; flex: none; color: var(--fc-brand-700); font-size: 12px; font-weight: 600; }
.rec-side { display: flex; flex-direction: column; align-items: flex-end; justify-content: center; gap: 7px; flex: none; }
.rec-order-btn { margin-left: 0 !important; }

/* ---------- 参团下单弹窗 ---------- */
.order-prod { display: flex; gap: 13px; margin-bottom: 16px; }
.order-thumb { width: 62px; height: 62px; border-radius: 14px; flex: none; display: grid; place-items: center; overflow: hidden; background: var(--fc-brand-50); color: var(--fc-brand-700); font-size: 24px; font-weight: 700; }
.order-thumb img { width: 100%; height: 100%; object-fit: cover; display: block; }
.order-info { flex: 1; min-width: 0; }
.order-info b { display: block; font-size: 14.5px; color: var(--fc-ink-900); margin-bottom: 3px; }
.order-info .fc-muted { display: block; font-size: 11.5px; margin-bottom: 5px; }
.order-row { display: flex; align-items: center; justify-content: space-between; padding: 10px 0; border-bottom: 1px dashed var(--fc-line-soft); font-size: 13.5px; }
.order-row .order-label { color: var(--fc-ink-400); }
.order-row .order-value { color: var(--fc-ink-800); font-weight: 600; }
.order-row.total { border-bottom: none; }
.order-note { margin-top: 12px; padding: 10px 13px; border-radius: var(--fc-r); background: var(--fc-brand-50); color: var(--fc-brand-700); font-size: 12px; line-height: 1.7; }

.answer-foot { margin-top: 14px; padding-top: 12px; border-top: 1px dashed var(--fc-line-soft); }

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
  .answer { flex-wrap: wrap; }
  .answer-items { grid-template-columns: 1fr; }
  .subscription { align-items: flex-start; padding: 14px; }
  .subscription-icon { width: 36px; height: 36px; }
  .subscription :deep(.el-button) { padding-inline: 5px; }
}
</style>

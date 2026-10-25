<template>
  <div class="gbd">
    <!-- 面包屑 -->
    <div class="crumb">
      <span @click="router.push('/user/groups')">团购广场</span>
      <el-icon><ArrowRight /></el-icon>
      <b>{{ detail.title || detail.productName || '团购详情' }}</b>
    </div>

    <SkeletonList v-if="loading" :rows="1" variant="card" />

    <EmptyState
      v-else-if="!detail.id"
      icon="🔍"
      title="团购不存在或已结束"
      description="它可能已经被解散，或者链接有误。"
      action-text="回到团购广场"
      @action="router.push('/user/groups')"
    />

    <template v-else>
      <div class="pd">
        <!-- 左：图片 -->
        <div class="pd-left">
          <div class="gallery" :class="detail.imageUrl ? '' : visual.gradient">
            <img v-if="detail.imageUrl" :src="detail.imageUrl" :alt="detail.title" />
            <span v-else class="gallery-emoji">{{ visual.emoji }}</span>
          </div>
        </div>

        <!-- 右：信息 -->
        <div class="pd-info">
          <h2>{{ detail.title || detail.productName }}</h2>

          <div class="meta-line">
            <span v-if="rating.count" class="star-line">
              <el-icon class="star"><StarFilled /></el-icon>
              <b>{{ rating.avgScore }}</b>
              <span>分 · {{ rating.count }} 条评价</span>
            </span>
            <span v-else class="fc-muted">暂无评价</span>
            <span class="dot">·</span>
            <span class="fc-muted">团长 {{ detail.leaderName }}</span>
          </div>

          <div v-if="insightTags.length" class="insight-row">
            <span class="insight-label">口碑</span>
            <el-tag v-for="tag in insightTags" :key="tag" type="success" effect="plain" size="small">
              {{ tag }}
            </el-tag>
          </div>

          <!-- 价格条 -->
          <div class="price-bar">
            <span class="fc-price big"><i>¥</i>{{ formatMoney(detail.price) }}</span>
            <span class="price-label">拼团价</span>
            <s v-if="detail.originalPrice">{{ formatPrice(detail.originalPrice) }}</s>
            <span v-if="saved" class="save">立省 {{ saved }} 元</span>
          </div>

          <!-- 参团进度 -->
          <div class="prog-card">
            <div class="prog-top">
              <b class="fc-num">{{ detail.memberCount }}</b>
              <span>/ {{ detail.targetCount }} 人已参团</span>
              <span v-if="left.expired" class="cd expired">已截止</span>
              <span v-else class="cd" :class="{ urgent: left.urgent }">
                <el-icon><Clock /></el-icon>{{ left.text }}
              </span>
            </div>
            <div class="fc-progress" style="height: 8px">
              <i :class="{ 'is-hot': left.urgent }" :style="{ width: percent + '%' }" />
            </div>
            <div class="prog-hint">
              <template v-if="gap > 0">🔥 还差 <b>{{ gap }} 人</b>成团，邀请邻居一起拼更划算</template>
              <template v-else>🎉 人数已达标，等待系统成团</template>
            </div>
          </div>

          <!-- 团长与提货点 -->
          <div class="leader-card">
            <div class="avatar">{{ (detail.leaderName || '?').charAt(0).toUpperCase() }}</div>
            <div class="leader-info">
              <b>{{ detail.leaderName }} · 团长</b>
              <span>
                <el-icon><LocationInformation /></el-icon>
                {{ detail.pickupPointName || '—' }}
                <template v-if="detail.pickupPointAddress"> · {{ detail.pickupPointAddress }}</template>
              </span>
            </div>
            <StatusBadge kind="groupBuy" :status="detail.status" />
            <img
              v-if="detail.pickupPointImageUrl"
              class="pickup-photo"
              :src="detail.pickupPointImageUrl"
              alt="提货点照片"
              loading="lazy"
            />
          </div>

          <!-- 下单 -->
          <div class="buy-row">
            <el-input-number v-model="quantity" :min="1" :max="99" size="large" />
            <el-button
              type="primary"
              size="large"
              class="buy-btn"
              :disabled="left.expired || detail.status !== 'ONGOING'"
              @click="openJoin"
            >
              <template v-if="left.expired">已截止</template>
              <template v-else-if="detail.status !== 'ONGOING'">已成团 · 去我的订单查看取货码</template>
              <template v-else>立即参团{{ gap > 0 ? ' · 还差 ' + gap + ' 人' : '' }}</template>
            </el-button>
          </div>

          <p class="fc-muted guarantee">
            <el-icon><CircleCheck /></el-icon>
            成团后凭取货码到提货点自提；截止前未达标将自动解散并全额退款
          </p>
        </div>
      </div>

      <!-- 下半部分 -->
      <div class="bottom">
        <div class="panel">
          <h3>商品信息</h3>
          <div class="kv">
            <span class="k">商品名</span>
            <span class="v">{{ detail.productName || product.name || '—' }}</span>
          </div>
          <div class="kv">
            <span class="k">零售价</span>
            <span class="v">
              <s v-if="detail.originalPrice" style="color: var(--fc-ink-300)">
                {{ formatPrice(detail.originalPrice) }}
              </s>
              <span style="margin-left: 6px; color: var(--fc-accent-600); font-weight: 700">
                拼团 {{ formatPrice(detail.price) }}
              </span>
            </span>
          </div>
          <div class="kv">
            <span class="k">库存</span>
            <span class="v">
              剩 {{ product.stock ?? '—' }} 件
              <span v-if="product.stock !== undefined && product.stock <= 10" class="low">库存偏低</span>
            </span>
          </div>
          <div class="kv"><span class="k">提货方式</span><span class="v">成团后凭取货码到店自提</span></div>
          <div class="kv">
            <span class="k">退款保障</span>
            <span class="v" style="color: var(--fc-brand-700)">未成团自动全额退款，无需申请</span>
          </div>
        </div>

        <div class="panel">
          <div class="panel-head">
            <h3>买家评价</h3>
            <span v-if="reviews.length" class="fc-muted">共 {{ reviews.length }} 条</span>
          </div>

          <div v-if="reviews.length === 0" class="no-review">这个商品还没有评价，完成订单后可以来说说感受。</div>

          <div v-for="r in reviews" :key="r.id" class="review">
            <div class="avatar sm">{{ (r.username || '?').charAt(0).toUpperCase() }}</div>
            <div class="review-body">
              <div class="review-head">
                <b>{{ r.username }}</b>
                <span>{{ fromNow(r.createdAt, now) }}</span>
                <span class="scores">
                  商品 {{ r.productScore }} 分 · 团长 {{ r.leaderScore }} 分
                </span>
              </div>
              <p v-if="r.content">{{ r.content }}</p>
              <div v-if="r.leaderReply" class="leader-reply">
                <b>团长回复</b><span>{{ r.leaderReply }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>

    <!-- 参团弹窗 -->
    <el-dialog v-model="dialogVisible" title="参加团购" width="420px">
      <div class="join-body">
        <div class="join-row">
          <span class="join-label">商品</span>
          <span class="join-value">{{ detail.title || detail.productName }}</span>
        </div>
        <div class="join-row">
          <span class="join-label">数量</span>
          <span class="join-value">{{ quantity }} 件</span>
        </div>
        <div class="join-row">
          <span class="join-label">提货点</span>
          <span class="join-value">{{ detail.pickupPointName || '—' }}</span>
        </div>
        <div class="join-row total">
          <span class="join-label">应付</span>
          <span class="join-value fc-price" style="font-size: 20px">
            <i>¥</i>{{ formatMoney(totalAmount) }}
          </span>
        </div>
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">确认下单</el-button>
      </template>
    </el-dialog>

    <!-- 移动端固定操作栏（≤768px，桌面隐藏）：滚动时拼团价与参团按钮始终可达 -->
    <div class="mobile-cta">
      <div class="m-price">
        <b class="fc-price"><i>¥</i>{{ formatMoney(detail.price) }}</b>
        <span>拼团价</span>
      </div>
      <el-button
        type="primary"
        size="large"
        class="m-btn"
        :disabled="left.expired || detail.status !== 'ONGOING'"
        @click="openJoin"
      >
        <template v-if="left.expired">已截止</template>
        <template v-else-if="detail.status !== 'ONGOING'">已成团 · 查取货码</template>
        <template v-else>立即参团{{ gap > 0 ? ' · 还差 ' + gap + ' 人' : '' }}</template>
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowRight, CircleCheck, Clock, LocationInformation, StarFilled
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import SkeletonList from '../components/SkeletonList.vue'
import EmptyState from '../components/EmptyState.vue'
import StatusBadge from '../components/StatusBadge.vue'
import { getGroupBuyDetail } from '../api/groupBuy'
import { getProduct } from '../api/product'
import { getProductReviews } from '../api/review'
import { createOrder } from '../api/order'
import { useNow } from '../composables/useNow'
import { productVisual } from '../utils/visual'
import { formatMoney, formatPrice, remaining, progressPercent, remainingMembers, fromNow } from '../utils/format'

const route = useRoute()
const router = useRouter()
const now = useNow()

const loading = ref(true)
const detail = ref({})
const product = ref({})
const reviews = ref([])
const rating = ref({ avgScore: 0, count: 0 })

const quantity = ref(1)
const dialogVisible = ref(false)
const submitting = ref(false)

const visual = computed(() =>
  productVisual(detail.value.productName || detail.value.title || '')
)
const percent = computed(() => progressPercent(detail.value.memberCount, detail.value.targetCount))
const left = computed(() => remaining(detail.value.deadline, now.value))
const gap = computed(() => remainingMembers(detail.value.memberCount, detail.value.targetCount))

const saved = computed(() => {
  const o = Number(detail.value.originalPrice)
  const p = Number(detail.value.price)
  if (!Number.isFinite(o) || !Number.isFinite(p) || o <= p) return ''
  return (o - p).toFixed(0)
})

const totalAmount = computed(() => Number(detail.value.price || 0) * quantity.value)

const insightTags = computed(() => detail.value.insightTags || product.value.insightTags || [])

async function load() {
  loading.value = true
  const id = route.params.id
  try {
    const res = await getGroupBuyDetail(id)
    detail.value = res.data || {}
  } catch (e) {
    detail.value = {}
    loading.value = false
    return
  }

  // 商品详情（库存）与评价是辅助信息，失败不影响主流程
  const pid = detail.value.productId
  if (pid) {
    try {
      const [p, r] = await Promise.all([getProduct(pid), getProductReviews(pid)])
      product.value = p.data || {}
      rating.value = (r.data && r.data.rating) || { avgScore: 0, count: 0 }
      reviews.value = (r.data && r.data.reviews) || []
    } catch (e) {
      product.value = {}
      reviews.value = []
    }
  }
  loading.value = false
}

function openJoin() {
  quantity.value = 1
  dialogVisible.value = true
}

async function submit() {
  submitting.value = true
  try {
    await createOrder(detail.value.id, quantity.value)
    ElMessage.success('下单成功，请去我的订单完成支付')
    dialogVisible.value = false
    router.push('/user/orders')
  } catch (e) {
    // 错误提示已由请求拦截器统一处理
  } finally {
    submitting.value = false
  }
}

onMounted(load)
watch(() => route.params.id, load)
</script>

<style scoped>
.gbd {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.crumb {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 12.5px;
  color: var(--fc-ink-400);
}

.crumb span {
  cursor: pointer;
}

.crumb span:hover {
  color: var(--fc-brand);
}

.crumb b {
  color: var(--fc-ink-700);
  font-weight: 600;
}

.crumb .el-icon {
  font-size: 12px;
}

/* ---------- 上半部分 ---------- */
.pd {
  display: grid;
  grid-template-columns: 400px 1fr;
  gap: 26px;
  align-items: start;
}

.gallery {
  height: 340px;
  border-radius: var(--fc-r-lg);
  display: grid;
  place-items: center;
  overflow: hidden;
  border: 1px solid var(--fc-line);
}

.gallery img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.gallery-emoji {
  font-size: 118px;
}

.pd-info h2 {
  margin: 0 0 10px;
  font-size: 22px;
  font-weight: 700;
  color: var(--fc-ink-900);
  letter-spacing: -.3px;
}

.meta-line {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12.5px;
  color: var(--fc-ink-400);
  flex-wrap: wrap;
}

.star-line {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.star-line .star {
  color: var(--fc-warning);
  font-size: 14px;
}

.star-line b {
  color: var(--fc-ink-700);
}

.dot {
  color: var(--fc-ink-300);
}

.insight-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  flex-wrap: wrap;
}

.insight-label {
  font-size: 12px;
  color: var(--fc-ink-400);
}

.price-bar {
  display: flex;
  align-items: baseline;
  gap: 12px;
  padding: 16px 20px;
  border-radius: var(--fc-r);
  background: linear-gradient(100deg, #FFF3E8, #FFF9F4);
  border: 1px solid #FFE3CB;
  margin: 16px 0;
}

.price-bar .big {
  font-size: 30px;
}

.price-label {
  font-size: 13px;
  color: var(--fc-ink-400);
}

.price-bar s {
  color: var(--fc-ink-300);
  font-size: 14px;
}

.save {
  margin-left: auto;
  font-size: 12px;
  font-weight: 700;
  color: var(--fc-accent-600);
  background: #fff;
  padding: 4px 10px;
  border-radius: var(--fc-r-pill);
  border: 1px solid #FFD9B8;
}

.prog-card {
  padding: 16px 18px;
  border-radius: var(--fc-r-lg);
  background: #fff;
  border: 1px solid var(--fc-line);
  box-shadow: var(--fc-sh-1);
  margin-bottom: 12px;
}

.prog-top {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 11px;
}

.prog-top b {
  font-size: 23px;
  font-weight: 750;
  color: var(--fc-brand-700);
  letter-spacing: -.5px;
}

.prog-top span {
  font-size: 12.5px;
  color: var(--fc-ink-400);
}

.cd {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12.5px;
  font-weight: 700;
  color: var(--fc-accent-600);
  background: var(--fc-accent-50);
  padding: 4px 10px;
  border-radius: var(--fc-r-pill);
}

.cd.urgent {
  color: var(--fc-danger);
  background: var(--fc-danger-50);
}

.cd.expired {
  color: var(--fc-ink-500);
  background: #F1F4F2;
}

.prog-hint {
  margin-top: 10px;
  font-size: 12px;
  color: var(--fc-ink-400);
}

.prog-hint b {
  color: var(--fc-accent-600);
}

.leader-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 15px 18px;
  border-radius: var(--fc-r-lg);
  background: #fff;
  border: 1px solid var(--fc-line);
  box-shadow: var(--fc-sh-1);
  margin-bottom: 18px;
}

.pickup-photo {
  margin-left: auto;
  width: 88px;
  height: 62px;
  border-radius: var(--fc-r-sm);
  object-fit: cover;
  border: 1px solid var(--fc-line-soft);
  flex: none;
}

.avatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  flex: none;
  display: grid;
  place-items: center;
  background: linear-gradient(140deg, #7ED0A4, #1E9E5A);
  color: #fff;
  font-size: 16px;
  font-weight: 650;
}

.avatar.sm {
  width: 32px;
  height: 32px;
  font-size: 12.5px;
}

.leader-info {
  flex: 1;
  min-width: 0;
}

.leader-info b {
  display: block;
  font-size: 14px;
  font-weight: 650;
  color: var(--fc-ink-900);
  margin-bottom: 3px;
}

.leader-info span {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--fc-ink-400);
}

.buy-row {
  display: flex;
  align-items: center;
  gap: 14px;
}

.buy-btn {
  flex: 1;
  height: 44px;
  font-size: 15px;
}

.guarantee {
  display: flex;
  align-items: center;
  gap: 5px;
  margin: 13px 0 0;
}

/* ---------- 下半部分 ---------- */
.bottom {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 18px;
  align-items: start;
}

.panel {
  background: #fff;
  border: 1px solid var(--fc-line);
  border-radius: var(--fc-r-lg);
  box-shadow: var(--fc-sh-1);
  padding: 20px;
}

.panel h3 {
  margin: 0 0 14px;
  font-size: 15px;
  font-weight: 650;
  color: var(--fc-ink-900);
}

.panel-head {
  display: flex;
  align-items: center;
  margin-bottom: 14px;
}

.panel-head h3 {
  margin: 0;
}

.panel-head .fc-muted {
  margin-left: auto;
}

.kv {
  display: flex;
  gap: 10px;
  padding: 9px 0;
  font-size: 13px;
  border-bottom: 1px dashed var(--fc-line-soft);
}

.kv:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.kv .k {
  width: 76px;
  flex: none;
  color: var(--fc-ink-400);
  font-size: 12.5px;
}

.kv .v {
  color: var(--fc-ink-700);
  font-weight: 500;
}

.low {
  margin-left: 6px;
  color: var(--fc-danger);
  font-size: 12px;
}

.leader-reply {
  margin-top: 8px;
  padding: 8px 12px;
  background: var(--fc-brand-50);
  border-left: 3px solid var(--fc-brand);
  border-radius: 0 var(--fc-r-sm) var(--fc-r-sm) 0;
  font-size: 12.5px;
  line-height: 1.7;
}

.leader-reply b {
  color: var(--fc-brand-700);
  margin-right: 8px;
}

.no-review {
  font-size: 13px;
  color: var(--fc-ink-400);
  padding: 12px 0;
}

.review {
  display: flex;
  gap: 11px;
  padding: 13px 0;
  border-bottom: 1px solid var(--fc-line-soft);
}

.review:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.review-body {
  flex: 1;
  min-width: 0;
}

.review-head {
  display: flex;
  align-items: center;
  gap: 9px;
  margin-bottom: 5px;
  flex-wrap: wrap;
}

.review-head b {
  font-size: 13px;
  color: var(--fc-ink-800);
  font-weight: 600;
}

.review-head span {
  font-size: 11.5px;
  color: var(--fc-ink-300);
}

.review-head .scores {
  margin-left: auto;
  color: var(--fc-warning);
  font-weight: 600;
}

.review p {
  margin: 0;
  font-size: 13px;
  color: var(--fc-ink-500);
  line-height: 1.65;
}

/* ---------- 弹窗 ---------- */
.join-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.join-row {
  display: flex;
  align-items: center;
}

.join-row.total {
  border-top: 1px dashed var(--fc-line);
  padding-top: 14px;
}

.join-label {
  font-size: 13.5px;
  color: var(--fc-ink-500);
}

.join-value {
  margin-left: auto;
  font-size: 13.5px;
  color: var(--fc-ink-800);
  font-weight: 600;
  text-align: right;
}

@media (max-width: 1000px) {
  .pd {
    grid-template-columns: 1fr;
  }

  .gallery {
    height: 260px;
  }

  .bottom {
    grid-template-columns: 1fr;
  }
}

/* ---------- 移动端固定操作栏：桌面隐藏，≤768px 固定底部 ---------- */
.mobile-cta {
  display: none;
}

@media (max-width: 768px) {
  /* 底部同时有 TabBar（约 64px + safe-area），操作栏必须叠在其上方；
     内容区预留 = 操作栏 74px + TabBar 64px + safe-area */
  .gbd {
    padding-bottom: calc(140px + env(safe-area-inset-bottom, 0px));
  }

  .mobile-cta {
    position: fixed;
    left: 0;
    right: 0;
    bottom: calc(64px + env(safe-area-inset-bottom, 0px));
    z-index: 90;
    display: flex;
    align-items: center;
    gap: 14px;
    padding: 10px 16px;
    background: #fff;
    border-top: 1px solid var(--fc-line);
    box-shadow: 0 -4px 16px rgba(15, 26, 20, .06);
  }

  .m-price {
    display: flex;
    flex-direction: column;
    line-height: 1.2;
  }

  .m-price b {
    font-size: 21px;
  }

  .m-price span {
    font-size: 11px;
    color: var(--fc-ink-400);
  }

  .m-btn {
    flex: 1;
    min-height: 46px;
    font-size: 15px;
  }
}
</style>

<template>
  <div class="home">
    <!-- 欢迎条 -->
    <section class="hero">
      <div class="hero-text">
        <span class="hero-tag">{{ greeting }}，{{ store.user?.username }}</span>
        <h2>{{ heroTitle }}</h2>
        <p>{{ heroDesc }}</p>
      </div>
      <div class="hero-emoji">🥬</div>
    </section>

    <!-- 数据概览 -->
    <section v-if="loading" class="grid">
      <SkeletonList :rows="1" variant="row" />
    </section>
    <section v-else class="grid">
      <StatCard
        v-for="s in stats"
        :key="s.label"
        :label="s.label"
        :value="s.value"
        :icon="s.icon"
        :tone="s.tone"
      />
    </section>

    <!-- 快捷入口 -->
    <section class="quick">
      <h3 class="fc-section-title">快捷入口</h3>
      <div class="quick-grid">
        <router-link v-for="q in quickLinks" :key="q.path" :to="q.path" class="quick-item">
          <span class="qi-icon" :class="'tone-' + q.tone">{{ q.icon }}</span>
          <span class="qi-text">
            <b>{{ q.label }}</b>
            <small>{{ q.desc }}</small>
          </span>
          <el-icon class="qi-arrow"><ArrowRight /></el-icon>
        </router-link>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ArrowRight } from '@element-plus/icons-vue'
import StatCard from '../components/StatCard.vue'
import SkeletonList from '../components/SkeletonList.vue'
import { useUserStore } from '../stores/user'
import { useNotificationStore } from '../stores/notification'
import { listMyOrders } from '../api/order'
import { listMyGroupBuys } from '../api/groupBuy'
import { getDashboard } from '../api/admin'

const store = useUserStore()
const unreadStore = useNotificationStore()

const loading = ref(true)
const remote = ref({})

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 11) return '早上好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const HERO = {
  USER: { title: '今天想拼点什么？', desc: '看看楼下正在进行中的团购，和邻居一起拼更划算。' },
  LEADER: { title: '你的团今天进展如何？', desc: '关注还差几人的团，及时催一催；备好货记得提醒邻居来取。' },
  ADMIN: { title: '平台今天运行正常', desc: '掌握订单与成团情况，留意库存偏低的商品。' }
}

const heroTitle = computed(() => (HERO[store.role] || HERO.USER).title)
const heroDesc = computed(() => (HERO[store.role] || HERO.USER).desc)

/* ---------------- 概览数据：按角色取最关心的几个指标 ---------------- */
const stats = computed(() => {
  const unread = unreadStore.unread

  if (store.role === 'ADMIN') {
    const d = remote.value.dashboard || {}
    return [
      { label: '今日订单数', value: d.todayOrders ?? '—', icon: '📋', tone: 'green' },
      { label: '今日成团数', value: d.todaySuccessGroups ?? '—', icon: '📦', tone: 'orange' },
      { label: '未读消息', value: unread, icon: '🔔', tone: 'blue' }
    ]
  }

  if (store.role === 'LEADER') {
    const gs = remote.value.myGroups || []
    return [
      { label: '我发起的团购', value: gs.length, icon: '🧺', tone: 'green' },
      { label: '进行中', value: gs.filter((g) => g.status === 'ONGOING').length, icon: '⏳', tone: 'orange' },
      { label: '已成团', value: gs.filter((g) => g.status === 'SUCCESS').length, icon: '✅', tone: 'blue' },
      { label: '未读消息', value: unread, icon: '🔔', tone: 'purple' }
    ]
  }

  const orders = remote.value.myOrders || []
  return [
    { label: '我的订单', value: orders.length, icon: '📋', tone: 'green' },
    { label: '待支付', value: orders.filter((o) => o.status === 'PENDING_PAYMENT').length, icon: '⏰', tone: 'orange' },
    { label: '待取货', value: orders.filter((o) => o.status === 'PENDING_PICKUP').length, icon: '🎫', tone: 'blue' },
    { label: '未读消息', value: unread, icon: '🔔', tone: 'purple' }
  ]
})

/* ---------------- 快捷入口 ---------------- */
const QUICK = {
  USER: [
    { label: '团购广场', desc: '看正在进行中的团', path: '/user/groups', icon: '🧺', tone: 'green' },
    { label: '商品浏览', desc: '按分类挑选生鲜', path: '/user/products', icon: '🥬', tone: 'blue' },
    { label: '我的订单', desc: '取货码与订单状态', path: '/user/orders', icon: '🎫', tone: 'orange' },
    { label: '消息中心', desc: '成团与核销通知', path: '/notifications', icon: '🔔', tone: 'purple' }
  ],
  LEADER: [
    { label: '我的团购', desc: '进度与订单明细', path: '/leader/groups', icon: '🧺', tone: 'green' },
    { label: '核销台', desc: '输入取货码核销', path: '/leader/verify', icon: '✅', tone: 'orange' },
    { label: '团购广场', desc: '看看邻居在拼什么', path: '/user/groups', icon: '🥬', tone: 'blue' },
    { label: '消息中心', desc: '核销与成团通知', path: '/notifications', icon: '🔔', tone: 'purple' }
  ],
  ADMIN: [
    { label: '数据看板', desc: '订单与成团概况', path: '/admin/dashboard', icon: '📊', tone: 'green' },
    { label: '商品管理', desc: '增删改查与库存', path: '/admin/products', icon: '📦', tone: 'orange' },
    { label: '消息中心', desc: '系统与退款通知', path: '/notifications', icon: '🔔', tone: 'blue' }
  ]
}

const quickLinks = computed(() => QUICK[store.role] || QUICK.USER)

/* ---------------- 加载 ---------------- */
async function loadRemote() {
  try {
    if (store.role === 'ADMIN') {
      const res = await getDashboard()
      remote.value = { dashboard: res.data || {} }
    } else if (store.role === 'LEADER') {
      const res = await listMyGroupBuys()
      remote.value = { myGroups: res.data || [] }
    } else {
      const res = await listMyOrders()
      remote.value = { myOrders: res.data || [] }
    }
  } catch (e) {
    // 概览是辅助信息，加载失败不阻断页面；错误提示已由请求拦截器统一处理
    remote.value = {}
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  unreadStore.refresh()
  loadRemote()
})
</script>

<style scoped>
.home {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.hero {
  position: relative;
  overflow: hidden;
  border-radius: var(--fc-r-xl);
  padding: 26px 30px;
  color: #fff;
  background: linear-gradient(120deg, #1E9E5A 0%, #178A4E 55%, #0F6B3C 100%);
  display: flex;
  align-items: center;
  gap: 20px;
}

.hero::before {
  content: "";
  position: absolute;
  width: 380px;
  height: 380px;
  border-radius: 50%;
  right: -110px;
  top: -170px;
  background: radial-gradient(circle, rgba(255, 255, 255, .14), transparent 68%);
}

.hero-text {
  position: relative;
  z-index: 2;
  min-width: 0;
}

.hero-tag {
  display: inline-flex;
  align-items: center;
  font-size: 12px;
  font-weight: 600;
  background: rgba(255, 255, 255, .17);
  border: 1px solid rgba(255, 255, 255, .26);
  padding: 4px 12px;
  border-radius: var(--fc-r-pill);
  margin-bottom: 12px;
}

.hero h2 {
  margin: 0 0 7px;
  font-size: 24px;
  font-weight: 750;
  letter-spacing: -.5px;
  line-height: 1.3;
}

.hero p {
  margin: 0;
  font-size: 13.5px;
  opacity: .84;
  line-height: 1.6;
}

.hero-emoji {
  position: relative;
  z-index: 2;
  margin-left: auto;
  font-size: 58px;
  flex: none;
  opacity: .95;
  filter: drop-shadow(0 8px 16px rgba(0, 0, 0, .18));
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
  gap: 16px;
}

.quick h3 {
  margin: 0 0 14px;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 14px;
}

.quick-item {
  display: flex;
  align-items: center;
  gap: 13px;
  padding: 15px 17px;
  background: var(--fc-card);
  border: 1px solid var(--fc-line);
  border-radius: var(--fc-r-lg);
  box-shadow: var(--fc-sh-1);
  text-decoration: none;
  transition: transform .18s ease, box-shadow .18s ease, border-color .18s ease;
}

.quick-item:hover {
  transform: translateY(-2px);
  box-shadow: var(--fc-sh-2);
  border-color: var(--fc-brand-100);
}

.qi-icon {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  flex: none;
  display: grid;
  place-items: center;
  font-size: 19px;
}

.tone-green { background: var(--fc-brand-50); }
.tone-orange { background: var(--fc-accent-50); }
.tone-blue { background: var(--fc-info-50); }
.tone-purple { background: var(--fc-purple-50); }

.qi-text {
  flex: 1;
  min-width: 0;
}

.qi-text b {
  display: block;
  font-size: 14px;
  font-weight: 650;
  color: var(--fc-ink-900);
  line-height: 1.35;
}

.qi-text small {
  font-size: 12px;
  color: var(--fc-ink-400);
}

.qi-arrow {
  flex: none;
  font-size: 15px;
  color: var(--fc-ink-300);
}

@media (max-width: 700px) {
  .hero {
    padding: 20px 20px;
  }

  .hero-emoji {
    display: none;
  }
}
</style>

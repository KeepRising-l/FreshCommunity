<template>
  <div class="app-shell">
    <!-- ============ 侧边导航 ============ -->
    <aside class="app-side">
      <div class="side-logo">
        <div class="mark">🥬</div>
        <div class="brand-text">
          <b>FreshCommunity</b>
          <small>社区生鲜团购</small>
        </div>
      </div>

      <nav class="side-nav">
        <div v-for="grp in menus" :key="grp.group" class="nav-grp">
          <label>{{ grp.group }}</label>
          <router-link
            v-for="item in grp.items"
            :key="item.path"
            :to="item.path"
            class="nav-item"
            :class="{ on: isActive(item.path) }"
          >
            <el-icon><component :is="item.icon" /></el-icon>
            <span class="nav-label">{{ item.label }}</span>
            <span v-if="item.key === 'notifications' && unreadStore.unread > 0" class="n-badge">
              {{ unreadStore.unread }}
            </span>
          </router-link>
        </div>
      </nav>

      <div class="side-foot">
        <div class="side-user clickable" title="个人中心" @click="router.push('/profile')">
          <div class="avatar">{{ avatarChar }}</div>
          <div class="user-text">
            <b>{{ store.user?.username || '未登录' }}</b>
            <small>{{ roleText }}</small>
          </div>
          <button class="logout-btn" title="退出登录" @click.stop="logout">
            <el-icon><SwitchButton /></el-icon>
          </button>
        </div>
      </div>
    </aside>

    <!-- ============ 主区域 ============ -->
    <div class="app-main">
      <header class="app-top">
        <div class="crumb">
          <span class="crumb-root">FreshCommunity</span>
          <el-icon class="crumb-sep"><ArrowRight /></el-icon>
          <b>{{ pageTitle }}</b>
        </div>

        <div class="top-search">
          <el-icon><Search /></el-icon>
          <input
            v-model="keyword"
            placeholder="搜索商品名称…"
            @keyup.enter="doSearch"
          />
        </div>

        <router-link to="/notifications" class="icon-btn" title="消息中心">
          <el-icon><Bell /></el-icon>
          <span v-if="unreadStore.unread > 0" class="dot-badge">{{ unreadStore.unread }}</span>
        </router-link>

        <div class="avatar top-avatar">{{ avatarChar }}</div>
      </header>

      <main class="app-body">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowRight, Bell, Box, Checked, DataAnalysis, Goods, MagicStick,
  Search, Sell, SwitchButton, Tickets, User
} from '@element-plus/icons-vue'
import { useUserStore } from '../stores/user'
import { useNotificationStore } from '../stores/notification'

const store = useUserStore()
const unreadStore = useNotificationStore()
const router = useRouter()
const route = useRoute()

const keyword = ref('')

const ROLE_TEXT = { ADMIN: '管理员', LEADER: '团长', USER: '用户' }
const roleText = computed(() => ROLE_TEXT[store.role] || '访客')
const avatarChar = computed(() => (store.user?.username || '?').charAt(0).toUpperCase())

/* ---------------- 菜单：按角色分组 ----------------
 * 顺序即「经营 → 选购 → 我的 → 平台管理」的信息优先级。
 * 团长本身也是社区居民，所以保留选购；管理员只关心平台侧。 */
const SHOP_GROUP = {
  group: '选购',
  items: [
    { key: 'groups', label: '团购广场', path: '/user/groups', icon: Sell },
    { key: 'products', label: '商品浏览', path: '/user/products', icon: Goods },
    { key: 'ai', label: 'AI 导购', path: '/user/ai', icon: MagicStick }
  ]
}

const MINE_GROUP = {
  group: '我的',
  items: [
    { key: 'orders', label: '我的订单', path: '/user/orders', icon: Tickets },
    { key: 'notifications', label: '消息中心', path: '/notifications', icon: Bell },
    { key: 'profile', label: '个人中心', path: '/profile', icon: User }
  ]
}

const MENUS = {
  USER: [SHOP_GROUP, MINE_GROUP],
  LEADER: [
    {
      group: '经营',
      items: [
        { key: 'my-groups', label: '我的团购', path: '/leader/groups', icon: Box },
        { key: 'verify', label: '核销台', path: '/leader/verify', icon: Checked }
      ]
    },
    SHOP_GROUP,
    MINE_GROUP
  ],
  ADMIN: [
    {
      group: '平台管理',
      items: [
        { key: 'dashboard', label: '数据看板', path: '/admin/dashboard', icon: DataAnalysis },
        { key: 'admin-products', label: '商品管理', path: '/admin/products', icon: Box },
        { key: 'admin-orders', label: '订单管理', path: '/admin/orders', icon: Tickets }
      ]
    },
    {
      group: '我的',
      items: [
        { key: 'notifications', label: '消息中心', path: '/notifications', icon: Bell },
        { key: 'profile', label: '个人中心', path: '/profile', icon: User }
      ]
    }
  ]
}

const menus = computed(() => MENUS[store.role] || MENUS.USER)

/** 扁平化菜单，用于反查当前页面标题 */
const flatItems = computed(() => menus.value.flatMap((g) => g.items))

const pageTitle = computed(() => {
  const hit = flatItems.value.find((i) => matchPath(i.path))
  if (hit) return hit.label
  // 不在菜单里的页面（订单/商品详情、个人中心、404 等）用路由 meta.title 兜底
  return route.meta?.title || '概览'
})

function matchPath(path) {
  return route.path === path || route.path.startsWith(path + '/')
}

function isActive(path) {
  return matchPath(path)
}

function doSearch() {
  const kw = keyword.value.trim()
  router.push({ path: '/user/products', query: kw ? { keyword: kw } : {} })
}

function logout() {
  store.clearUser()
  router.push('/login')
}

onMounted(() => unreadStore.refresh())
watch(() => route.path, () => unreadStore.refresh())
</script>

<style scoped>
.app-shell {
  display: flex;
  height: 100vh;
  background: var(--fc-bg);
}

/* ---------------- 侧边栏 ---------------- */
.app-side {
  width: var(--fc-side-w);
  flex: none;
  background: #fff;
  border-right: 1px solid var(--fc-line);
  display: flex;
  flex-direction: column;
}

.side-logo {
  height: var(--fc-top-h);
  flex: none;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 18px;
  border-bottom: 1px solid var(--fc-line-soft);
}

.side-logo .mark {
  width: 32px;
  height: 32px;
  border-radius: 10px;
  flex: none;
  display: grid;
  place-items: center;
  font-size: 17px;
  background: linear-gradient(140deg, #2BB673, #12713F);
  box-shadow: 0 3px 10px rgba(30, 158, 90, .3);
}

.brand-text b {
  display: block;
  font-size: 14.5px;
  font-weight: 650;
  color: var(--fc-ink-900);
  letter-spacing: .2px;
  line-height: 1.25;
}

.brand-text small {
  display: block;
  font-size: 10.5px;
  color: var(--fc-ink-400);
  letter-spacing: .4px;
}

.side-nav {
  flex: 1;
  overflow-y: auto;
  padding: 14px 12px;
}

.nav-grp {
  margin-bottom: 16px;
}

.nav-grp > label {
  display: block;
  font-size: 10.5px;
  font-weight: 700;
  letter-spacing: 1px;
  color: var(--fc-ink-300);
  padding: 0 10px 7px;
}

.nav-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 9px 11px;
  border-radius: 10px;
  font-size: 13.5px;
  font-weight: 500;
  color: var(--fc-ink-500);
  text-decoration: none;
  margin-bottom: 2px;
  transition: background .15s ease, color .15s ease;
}

.nav-item:hover {
  background: var(--fc-bg);
  color: var(--fc-ink-800);
}

.nav-item.on {
  background: var(--fc-brand-50);
  color: var(--fc-brand-700);
  font-weight: 600;
}

.nav-item.on::before {
  content: "";
  position: absolute;
  left: -12px;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 18px;
  border-radius: 0 3px 3px 0;
  background: var(--fc-brand);
}

.nav-item .el-icon {
  font-size: 18px;
  flex: none;
  opacity: .85;
}

.nav-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.n-badge {
  margin-left: auto;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: var(--fc-danger);
  color: #fff;
  font-size: 10.5px;
  font-weight: 600;
  display: grid;
  place-items: center;
  line-height: 1;
}

.side-foot {
  flex: none;
  padding: 12px;
  border-top: 1px solid var(--fc-line-soft);
}

.side-user {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 10px;
  border-radius: 11px;
  background: var(--fc-card-2);
  border: 1px solid var(--fc-line-soft);
}

.side-user.clickable {
  cursor: pointer;
  transition: border-color .15s ease, box-shadow .15s ease;
}

.side-user.clickable:hover {
  border-color: var(--fc-brand-100);
  box-shadow: var(--fc-sh-1);
}

.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  flex: none;
  display: grid;
  place-items: center;
  background: linear-gradient(140deg, #7ED0A4, #1E9E5A);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
}

.user-text {
  flex: 1;
  min-width: 0;
}

.user-text b {
  display: block;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--fc-ink-800);
  line-height: 1.25;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-text small {
  font-size: 10.5px;
  color: var(--fc-ink-400);
}

.logout-btn {
  flex: none;
  width: 30px;
  height: 30px;
  border: none;
  border-radius: 9px;
  background: transparent;
  color: var(--fc-ink-400);
  cursor: pointer;
  display: grid;
  place-items: center;
  transition: background .15s ease, color .15s ease;
}

.logout-btn:hover {
  background: var(--fc-danger-50);
  color: var(--fc-danger);
}

/* ---------------- 主区域 ---------------- */
.app-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.app-top {
  height: var(--fc-top-h);
  flex: none;
  background: #fff;
  border-bottom: 1px solid var(--fc-line);
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 0 24px;
}

.crumb {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 13px;
  color: var(--fc-ink-400);
  min-width: 0;
}

.crumb-root {
  white-space: nowrap;
}

.crumb-sep {
  font-size: 12px;
}

.crumb b {
  color: var(--fc-ink-900);
  font-weight: 600;
  font-size: 15px;
  white-space: nowrap;
}

.top-search {
  margin-left: auto;
  width: 280px;
  height: 38px;
  border-radius: var(--fc-r-pill);
  background: var(--fc-bg);
  border: 1px solid transparent;
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 0 15px;
  transition: border-color .18s ease, background .18s ease;
}

.top-search:hover,
.top-search:focus-within {
  border-color: var(--fc-line);
  background: #fff;
}

.top-search .el-icon {
  font-size: 16px;
  color: var(--fc-ink-400);
  flex: none;
}

.top-search input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  font-family: inherit;
  font-size: 13px;
  color: var(--fc-ink-800);
}

.top-search input::placeholder {
  color: var(--fc-ink-300);
}

.icon-btn {
  position: relative;
  flex: none;
  width: 38px;
  height: 38px;
  border-radius: 11px;
  display: grid;
  place-items: center;
  color: var(--fc-ink-500);
  text-decoration: none;
  transition: background .15s ease, color .15s ease;
}

.icon-btn:hover {
  background: var(--fc-bg);
  color: var(--fc-ink-800);
}

.icon-btn .el-icon {
  font-size: 19px;
}

.dot-badge {
  position: absolute;
  top: 5px;
  right: 5px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 8px;
  background: var(--fc-danger);
  color: #fff;
  font-size: 10px;
  font-weight: 600;
  display: grid;
  place-items: center;
  line-height: 1;
  border: 2px solid #fff;
}

.top-avatar {
  width: 36px;
  height: 36px;
  font-size: 14px;
}

.app-body {
  flex: 1;
  overflow-y: auto;
  padding: 24px 28px 40px;
}

/* ---------------- 响应式：窄屏收起文字，只留图标 ---------------- */
@media (max-width: 900px) {
  .app-side {
    width: 68px;
  }

  .brand-text,
  .nav-label,
  .nav-grp > label,
  .user-text {
    display: none;
  }

  .side-logo {
    justify-content: center;
    padding: 0;
  }

  .nav-item {
    justify-content: center;
    padding: 10px 0;
  }

  .n-badge {
    position: absolute;
    top: 4px;
    right: 8px;
    margin-left: 0;
  }

  .side-user {
    justify-content: center;
    padding: 8px;
  }

  .logout-btn {
    display: none;
  }

  .top-search {
    width: 160px;
  }

  .crumb-root,
  .crumb-sep {
    display: none;
  }

  .app-body {
    padding: 16px 14px 32px;
  }
}
</style>

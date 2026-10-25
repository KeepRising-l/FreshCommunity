import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../stores/user'
import Login from '../views/Login.vue'
import Register from '../views/Register.vue'
import Dashboard from '../views/Dashboard.vue'

const routes = [
  { path: '/login', name: 'Login', component: Login, meta: { public: true } },
  { path: '/register', name: 'Register', component: Register, meta: { public: true } },
  {
    path: '/',
    component: Dashboard,
    redirect: '/dashboard',
    children: [
      { path: '/dashboard', name: 'Home', component: () => import('../views/Home.vue'), meta: { title: '概览' } },
      // 用户与团长共用选购入口（团长也是社区居民）
      { path: '/user/groups', name: 'UserGroups', component: () => import('../views/UserGroups.vue'), meta: { title: '团购广场', roles: ['USER', 'LEADER'] } },
      { path: '/user/groups/:id', name: 'GroupBuyDetail', component: () => import('../views/GroupBuyDetail.vue'), meta: { title: '团购详情', roles: ['USER', 'LEADER'] } },
      { path: '/user/orders', name: 'UserOrders', component: () => import('../views/UserOrders.vue'), meta: { title: '我的订单', roles: ['USER', 'LEADER'] } },
      { path: '/user/orders/:id', name: 'OrderDetail', component: () => import('../views/OrderDetail.vue'), meta: { title: '订单详情', roles: ['USER', 'LEADER'] } },
      { path: '/user/ai', name: 'AiShoppingAssistant', component: () => import('../views/AiShoppingAssistant.vue'), meta: { title: 'AI 导购', roles: ['USER', 'LEADER'] } },
      { path: '/user/products', name: 'ProductList', component: () => import('../views/ProductList.vue'), meta: { title: '商品浏览', roles: ['USER', 'LEADER'] } },
      { path: '/user/products/:id', name: 'ProductDetail', component: () => import('../views/ProductDetail.vue'), meta: { title: '商品详情', roles: ['USER', 'LEADER'] } },
      { path: '/leader/groups', name: 'LeaderGroups', component: () => import('../views/LeaderGroups.vue'), meta: { title: '我的团购', roles: ['LEADER'] } },
      { path: '/leader/verify', name: 'LeaderVerify', component: () => import('../views/LeaderVerify.vue'), meta: { title: '核销台', roles: ['LEADER'] } },
      { path: '/leader/reviews', name: 'LeaderReviews', component: () => import('../views/LeaderReviews.vue'), meta: { title: '评价与回复', roles: ['LEADER'] } },
      { path: '/admin/dashboard', name: 'AdminDashboard', component: () => import('../views/AdminDashboard.vue'), meta: { title: '数据看板', roles: ['ADMIN'] } },
      { path: '/admin/products', name: 'AdminProducts', component: () => import('../views/AdminProducts.vue'), meta: { title: '商品管理', roles: ['ADMIN'] } },
      { path: '/admin/orders', name: 'AdminOrders', component: () => import('../views/AdminOrders.vue'), meta: { title: '订单管理', roles: ['ADMIN'] } },
      { path: '/notifications', name: 'MyNotifications', component: () => import('../views/MyNotifications.vue'), meta: { title: '消息中心' } },
      { path: '/profile', name: 'Profile', component: () => import('../views/Profile.vue'), meta: { title: '个人中心' } },
      { path: '/:pathMatch(.*)*', name: 'NotFound', component: () => import('../views/NotFound.vue'), meta: { title: '页面不存在' } }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

const PUBLIC_PATHS = ['/login', '/register']

router.beforeEach((to, from, next) => {
  const store = useUserStore()

  // 未登录：一律去登录页（记住来路，登录后回跳）
  if (!PUBLIC_PATHS.includes(to.path) && !store.token) {
    next({ path: '/login', query: to.path === '/dashboard' ? {} : { redirect: to.fullPath } })
    return
  }

  // 已登录访问登录/注册页：直接回首页
  if (PUBLIC_PATHS.includes(to.path) && store.token) {
    next('/dashboard')
    return
  }

  // 角色守卫（体验层拦截；后端 @PreAuthorize 仍是最终防线）
  const roles = to.meta?.roles
  if (roles && roles.length > 0 && store.role && !roles.includes(store.role)) {
    ElMessage.warning('当前角色无权访问该页面')
    next('/dashboard')
    return
  }

  next()
})

export default router

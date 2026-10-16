import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../stores/user'
import Login from '../views/Login.vue'
import Register from '../views/Register.vue'
import Dashboard from '../views/Dashboard.vue'

const routes = [
  { path: '/login', name: 'Login', component: Login },
  { path: '/register', name: 'Register', component: Register },
  {
    path: '/',
    component: Dashboard,
    redirect: '/dashboard',
    children: [
      { path: '/dashboard', name: 'Dashboard', component: () => import('../views/Home.vue') },
      { path: '/admin/products', component: () => import('../views/AdminProducts.vue') },
      { path: '/admin/dashboard', component: () => import('../views/AdminDashboard.vue') },
      { path: '/leader/groups', component: () => import('../views/LeaderGroups.vue') },
      { path: '/leader/verify', component: () => import('../views/LeaderVerify.vue') },
      { path: '/user/groups', component: () => import('../views/UserGroups.vue') },
      { path: '/user/groups/:id', component: () => import('../views/GroupBuyDetail.vue') },
      { path: '/user/orders', component: () => import('../views/UserOrders.vue') },
      { path: '/user/ai', component: () => import('../views/AiShoppingAssistant.vue') },
      { path: '/user/products', component: () => import('../views/ProductList.vue') },
      { path: '/user/products/:id', component: () => import('../views/ProductDetail.vue') },
      { path: '/notifications', component: () => import('../views/MyNotifications.vue') }
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
  if (!PUBLIC_PATHS.includes(to.path) && !store.token) {
    next('/login')
  } else {
    next()
  }
})

export default router

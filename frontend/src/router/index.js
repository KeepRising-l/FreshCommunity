import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../stores/user'
import Login from '../views/Login.vue'
import Dashboard from '../views/Dashboard.vue'

const routes = [
  { path: '/login', name: 'Login', component: Login },
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
      { path: '/user/orders', component: () => import('../views/UserOrders.vue') }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const store = useUserStore()
  if (to.path !== '/login' && !store.token) {
    next('/login')
  } else {
    next()
  }
})

export default router

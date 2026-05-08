import { createRouter, createWebHistory } from 'vue-router'
import { getTokenStorage } from '@/api/tokenStorage'

// D1-W3 起加入登录/注册路由与守卫；W4 落地主壳层后再把 baseline 替换为正式主页面。
const router = createRouter({
  history: createWebHistory('/web/'),
  routes: [
    {
      path: '/',
      name: 'baseline',
      component: () => import('../views/BaselineView.vue'),
      meta: { requiresAuth: false }
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/LoginView.vue'),
      meta: { requiresAuth: false, hideForAuthed: true }
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('../views/RegisterView.vue'),
      meta: { requiresAuth: false, hideForAuthed: true }
    }
  ]
})

router.beforeEach((to) => {
  const hasToken = Boolean(getTokenStorage().getAccessToken())
  if (to.meta && to.meta.requiresAuth && !hasToken) {
    return {
      path: '/login',
      query: { redirect: to.fullPath }
    }
  }
  if (to.meta && to.meta.hideForAuthed && hasToken) {
    return { path: '/' }
  }
  return true
})

export default router

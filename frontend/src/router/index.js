import { createRouter, createWebHistory } from 'vue-router'

// W4 主壳层与导航尚未实现，当前路由表只承担 W0/W1 工程基线验证。
// 后续 W3/W4/W5 等任务落地时再扩展页面与登录守卫。
const router = createRouter({
  history: createWebHistory('/web/'),
  routes: [
    {
      path: '/',
      name: 'baseline',
      component: () => import('../views/BaselineView.vue'),
      meta: { requiresAuth: false }
    }
  ]
})

export default router

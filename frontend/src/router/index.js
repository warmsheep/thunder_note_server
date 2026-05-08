import { createRouter, createWebHistory } from 'vue-router'
import { getTokenStorage } from '@/api/tokenStorage'

// D1-W4 嵌套路由：MainShell 作为受保护壳，五个 tab 作为 children；
// /baseline 保留为公开调试入口；/login、/register 公开。
const router = createRouter({
  history: createWebHistory('/'),
  routes: [
    {
      path: '/baseline',
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
    },
    {
      path: '/',
      component: () => import('../layouts/MainShell.vue'),
      meta: { requiresAuth: true },
      children: [
        { path: '', redirect: '/notes' },
        {
          path: 'notes',
          name: 'notes',
          component: () => import('../views/NotesView.vue'),
          meta: { requiresAuth: true }
        },
        {
          path: 'collections',
          name: 'collections',
          component: () => import('../views/CollectionsView.vue'),
          meta: { requiresAuth: true }
        },
        {
          path: 'favorites',
          name: 'favorites',
          component: () => import('../views/FavoritesView.vue'),
          meta: { requiresAuth: true }
        },
        {
          path: 'search',
          name: 'search',
          component: () => import('../views/SearchView.vue'),
          meta: { requiresAuth: true }
        },
        {
          path: 'profile',
          name: 'profile',
          component: () => import('../views/ProfileView.vue'),
          meta: { requiresAuth: true }
        },
        {
          path: 'contacts',
          name: 'contacts',
          component: () => import('../views/ContactsView.vue'),
          meta: { requiresAuth: true }
        }
      ]
    },
    {
      path: '/chat/:flashNoteId(-?\\d+)',
      name: 'chat',
      component: () => import('../views/ChatView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/'
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

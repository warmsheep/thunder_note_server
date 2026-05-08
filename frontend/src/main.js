import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { configureApiClient } from './api/client'
import { refreshToken as refreshTokenApi } from './api/auth'
import { useAuthStore } from './stores/auth'
import './styles/global.css'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)
app.use(router)

// D1-W2-04：在 Pinia 就绪后把 apiClient 与 auth store 连接起来
// - refresh：由 authApi 直接完成，避免 authStore 与 apiClient 循环依赖
// - onUnauthorized：清空本地登录态，并引导回登录页（W3 落地后由 router 接管跳转）
configureApiClient({
  refresh: async (token) => {
    return refreshTokenApi(token)
  },
  onUnauthorized: () => {
    try {
      const authStore = useAuthStore()
      authStore.clearLocal()
    } catch (_e) {
      // Pinia 未就绪时兜底
    }
    // 登录态失效后引导回登录页；记录原路径供登录后回跳
    try {
      const current = router.currentRoute.value
      if (!current || current.name === 'login') return
      router.replace({
        path: '/login',
        query: { redirect: current.fullPath }
      })
    } catch (_e) {
      // router 未就绪或异常时不阻塞主流程
    }
  }
})

app.mount('#app')

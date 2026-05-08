import { defineStore } from 'pinia'
import { getTokenStorage } from '@/api/tokenStorage'
import * as authApi from '@/api/auth'

// D1-W2-03 Pinia auth store
// - 负责登录、刷新、登出等登录态流转
// - token 真正的"持久化"在 tokenStorage（默认 sessionStorage），store 只保留当前会话 user 信息
// - 登出时一并清除 tokenStorage 与 store 状态

const USER_KEY = 'tn.web.user'

function safeLoadUser() {
  try {
    if (typeof sessionStorage === 'undefined') return null
    const raw = sessionStorage.getItem(USER_KEY)
    return raw ? JSON.parse(raw) : null
  } catch (_e) {
    return null
  }
}

function safeSaveUser(user) {
  try {
    if (typeof sessionStorage === 'undefined') return
    if (user) {
      sessionStorage.setItem(USER_KEY, JSON.stringify(user))
    } else {
      sessionStorage.removeItem(USER_KEY)
    }
  } catch (_e) {
    // ignore
  }
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: safeLoadUser()
  }),
  getters: {
    isAuthenticated(state) {
      return Boolean(state.user) && Boolean(getTokenStorage().getAccessToken())
    },
    displayName(state) {
      if (!state.user) return ''
      return state.user.nickname || state.user.username || ''
    }
  },
  actions: {
    async login(credentials) {
      const resp = await authApi.login(credentials)
      if (!resp || !resp.accessToken) {
        throw new Error('Login response missing accessToken')
      }
      getTokenStorage().setTokens({
        accessToken: resp.accessToken,
        refreshToken: resp.refreshToken
      })
      this.user = resp.user || null
      safeSaveUser(this.user)
      return resp
    },
    async refresh(refreshTokenValue) {
      const resp = await authApi.refreshToken(refreshTokenValue)
      if (!resp || !resp.accessToken) {
        throw new Error('Refresh response missing accessToken')
      }
      getTokenStorage().setTokens({
        accessToken: resp.accessToken,
        refreshToken: resp.refreshToken || refreshTokenValue
      })
      if (resp.user) {
        this.user = resp.user
        safeSaveUser(this.user)
      }
      return resp
    },
    async logout({ silent = false } = {}) {
      if (!silent) {
        try {
          await authApi.logout()
        } catch (_e) {
          // 后端登出失败不应阻塞前端清空登录态
        }
      }
      this.clearLocal()
    },
    clearLocal() {
      getTokenStorage().clear()
      this.user = null
      safeSaveUser(null)
    }
  }
})

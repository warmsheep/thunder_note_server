// D1-W2-03 token 存储抽象。
// 默认实现使用 sessionStorage：
// - access/refresh token 随标签页关闭失效，降低 XSS 长期暴露面
// - 页面刷新保留登录态，避免每次 reload 都重新登录
// 测试场景可以通过 setTokenStorage() 注入内存实现，避免污染全局

const ACCESS_KEY = 'tn.web.accessToken'
const REFRESH_KEY = 'tn.web.refreshToken'

function safeSessionStorage() {
  try {
    if (typeof globalThis !== 'undefined' && globalThis.sessionStorage) {
      return globalThis.sessionStorage
    }
  } catch (_e) {
    // ignore，SSR 或无存储环境
  }
  return null
}

function createSessionTokenStorage() {
  return {
    getAccessToken() {
      const s = safeSessionStorage()
      return s ? s.getItem(ACCESS_KEY) : null
    },
    getRefreshToken() {
      const s = safeSessionStorage()
      return s ? s.getItem(REFRESH_KEY) : null
    },
    setTokens({ accessToken, refreshToken }) {
      const s = safeSessionStorage()
      if (!s) return
      if (accessToken) {
        s.setItem(ACCESS_KEY, accessToken)
      } else {
        s.removeItem(ACCESS_KEY)
      }
      if (refreshToken) {
        s.setItem(REFRESH_KEY, refreshToken)
      } else {
        s.removeItem(REFRESH_KEY)
      }
    },
    clear() {
      const s = safeSessionStorage()
      if (!s) return
      s.removeItem(ACCESS_KEY)
      s.removeItem(REFRESH_KEY)
    }
  }
}

let tokenStorage = createSessionTokenStorage()

export function getTokenStorage() {
  return tokenStorage
}

export function setTokenStorage(impl) {
  if (impl && typeof impl.getAccessToken === 'function') {
    tokenStorage = impl
  }
}

export function resetTokenStorage() {
  tokenStorage = createSessionTokenStorage()
}

export function createMemoryTokenStorage(initial = {}) {
  let access = initial.accessToken || null
  let refresh = initial.refreshToken || null
  return {
    getAccessToken() {
      return access
    },
    getRefreshToken() {
      return refresh
    },
    setTokens({ accessToken, refreshToken }) {
      access = accessToken || null
      refresh = refreshToken || null
    },
    clear() {
      access = null
      refresh = null
    }
  }
}

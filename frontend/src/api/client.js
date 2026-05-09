import axios from 'axios'
import { getTokenStorage } from './tokenStorage'

// D1-W2-01 / D1-W2-02 / D1-W2-04 统一 apiClient
// - 同源走 /api/...，dev 模式由 vite proxy 转发到后端 8080
// - 自动注入 Bearer access token，除非调用方显式 skipAuth
// - 自动解包 ApiResponse 的 data 字段，异常时抛出带 code/message 的 Error
// - 401 时尝试使用 refresh token 自动续期，并 replay 原请求
// - 续期失败或无 refresh token 时清空本地登录态并通知上层 onUnauthorized

const HTTP_TIMEOUT_MS = 15000

// 注入点：由 main.js 在 Pinia 就绪后 configureApiClient() 绑定
let refreshFn = null
let onUnauthorized = null
let ongoingRefresh = null

export function configureApiClient(options = {}) {
  if (typeof options.refresh === 'function') {
    refreshFn = options.refresh
  }
  if (typeof options.onUnauthorized === 'function') {
    onUnauthorized = options.onUnauthorized
  }
}

export function __resetApiClientForTests() {
  refreshFn = null
  onUnauthorized = null
  ongoingRefresh = null
}

// 注意：不要在这里设置默认 Content-Type。
// axios 1.x 的 transformRequest 会根据 data 类型自动处理：
//   - FormData → 浏览器自动写入 'multipart/form-data; boundary=...'
//   - 普通 object → 自动写入 'application/json'
// 一旦在 instance defaults 上写死 'application/json'，FormData 上传时
// Content-Type 不会被覆盖，后端会抛 "Current request is not a multipart request"。
const raw = axios.create({
  baseURL: '/',
  timeout: HTTP_TIMEOUT_MS
})

raw.interceptors.request.use((config) => {
  if (config && config.skipAuth) {
    return config
  }
  try {
    const token = getTokenStorage().getAccessToken()
    if (token && config.headers && !config.headers.Authorization) {
      config.headers.Authorization = `Bearer ${token}`
    }
  } catch (_e) {
    // 读取 token 失败不阻塞请求，由后端按未认证处理
  }
  return config
})

raw.interceptors.response.use(
  (resp) => resp,
  async (error) => {
    const status = error && error.response ? error.response.status : null
    const config = error ? error.config : null
    const canRetry = config && !config._retriedAfterRefresh && !config.skipAuth

    if (status !== 401 || !canRetry) {
      return Promise.reject(error)
    }

    const storage = getTokenStorage()
    const refreshToken = storage.getRefreshToken()

    if (!refreshToken || !refreshFn) {
      storage.clear()
      notifyUnauthorized()
      return Promise.reject(error)
    }

    try {
      if (!ongoingRefresh) {
        ongoingRefresh = Promise.resolve()
          .then(() => refreshFn(refreshToken))
          .finally(() => {
            // ongoingRefresh 只用作并发去重；reset 发生在下方根据成败分支
          })
      }
      const refreshed = await ongoingRefresh
      ongoingRefresh = null

      if (!refreshed || !refreshed.accessToken) {
        throw new Error('Refresh response missing accessToken')
      }
      storage.setTokens({
        accessToken: refreshed.accessToken,
        refreshToken: refreshed.refreshToken || refreshToken
      })

      config._retriedAfterRefresh = true
      config.headers = config.headers || {}
      config.headers.Authorization = `Bearer ${refreshed.accessToken}`
      return raw(config)
    } catch (refreshError) {
      ongoingRefresh = null
      storage.clear()
      notifyUnauthorized()
      return Promise.reject(refreshError)
    }
  }
)

function notifyUnauthorized() {
  if (!onUnauthorized) {
    return
  }
  try {
    onUnauthorized()
  } catch (_e) {
    // 忽略上层回调异常，避免掩盖原始网络错误
  }
}

function unwrap(resp) {
  const body = resp && resp.data
  if (body && typeof body === 'object' && 'code' in body && 'data' in body) {
    if (body.code === 0) {
      return body.data
    }
    const err = new Error(body.message || `请求失败 (code=${body.code})`)
    err.code = body.code
    err.serverMessage = body.message
    throw err
  }
  return body
}

const apiClient = {
  raw,
  async get(url, params, config) {
    const merged = Object.assign({ params }, config || {})
    const resp = await raw.get(url, merged)
    return unwrap(resp)
  },
  async post(url, data, config) {
    const resp = await raw.post(url, data, config)
    return unwrap(resp)
  },
  async put(url, data, config) {
    const resp = await raw.put(url, data, config)
    return unwrap(resp)
  },
  async delete(url, config) {
    const resp = await raw.delete(url, config)
    return unwrap(resp)
  }
}

export default apiClient

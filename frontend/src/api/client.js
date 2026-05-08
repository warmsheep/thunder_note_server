import axios from 'axios'

// D1-W2-01 / D1-W2-02 统一 apiClient
// - 同源走 /api/...，dev 模式由 vite proxy 转发到后端 8080
// - 自动解包 ApiResponse 的 data 字段
// - W2-03 / W2-04 落地后再补 token 注入与 401 / refresh 处理

const HTTP_TIMEOUT_MS = 15000

const raw = axios.create({
  baseURL: '/',
  timeout: HTTP_TIMEOUT_MS,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 占位 token getter；W2-03 接入 Pinia 后替换
let tokenProvider = () => null

export function setTokenProvider(fn) {
  if (typeof fn === 'function') {
    tokenProvider = fn
  }
}

raw.interceptors.request.use((config) => {
  try {
    const token = tokenProvider()
    if (token && !config.headers.Authorization) {
      config.headers.Authorization = `Bearer ${token}`
    }
  } catch (_e) {
    // tokenProvider 出错不阻塞请求；W2-04 完整接入登录态后再补错误处理
  }
  return config
})

function unwrap(resp) {
  const body = resp?.data
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
  async get(url, params) {
    const resp = await raw.get(url, { params })
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

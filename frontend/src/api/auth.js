import apiClient from './client'

// D1-W2 认证相关 API 模块，对齐后端真实 AuthController：
// - 真实路径 /api/auth/...（不是 /api/v1/...）
// - 登录字段是 username（不是 email）
// - 登录/刷新响应：{ accessToken, refreshToken, tokenType, expiresIn, user }
// 登录、注册、刷新请求显式 skipAuth，避免被 interceptor 附加旧 token

export function login({ username, password }) {
  return apiClient.post(
    '/api/auth/login',
    { username, password },
    { skipAuth: true }
  )
}

export function register(payload) {
  return apiClient.post('/api/auth/register', payload, { skipAuth: true })
}

export function refreshToken(token) {
  return apiClient.post(
    '/api/auth/refresh',
    { refreshToken: token },
    { skipAuth: true }
  )
}

export function logout() {
  // Authorization header 由 request interceptor 自动注入
  return apiClient.post('/api/auth/logout')
}

// D1-W15 修改密码：后端 DTO 字段是 currentPassword（不是 oldPassword）
// 后端 @Size(min = 6) 校验 newPassword，前端额外做 confirm 一致性校验
export function changePassword({ currentPassword, newPassword }) {
  if (!currentPassword) {
    return Promise.reject(new Error('currentPassword is required'))
  }
  if (!newPassword) {
    return Promise.reject(new Error('newPassword is required'))
  }
  return apiClient.put('/api/auth/password', { currentPassword, newPassword })
}

import apiClient from './client'

// D1-W11 用户资料 API 封装
// - getProfile：注意是 POST（不是 GET），后端 controller 实际声明是 POST /api/users/profile
// - updateProfile：PUT /api/users/profile，body 是完整 UserProfile 实体（nickname/bio/preferencesJson 等）
// - updateAvatar：PUT /api/users/avatar，body { avatar: <URL> }，avatar 必须是合法 URL（@URL 校验，<= 2048）

export function getProfile() {
  return apiClient.post('/api/users/profile')
}

export function updateProfile(profile) {
  const body = profile && typeof profile === 'object' ? { ...profile } : {}
  return apiClient.put('/api/users/profile', body)
}

export function updateAvatar(avatarUrl) {
  if (!avatarUrl) {
    return Promise.reject(new Error('avatar URL is required'))
  }
  return apiClient.put('/api/users/avatar', { avatar: String(avatarUrl) })
}

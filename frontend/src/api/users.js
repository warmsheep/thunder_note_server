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

// D1-W14 联系人与好友请求 API
// 后端 relationStatus 取值：FRIEND / PENDING_SENT / PENDING_RECEIVED / NONE
// 联系人列表已包含"我发出的 PENDING_SENT"，"对方发给我的 PENDING_RECEIVED" 走好友请求列表

export function listContacts() {
  return apiClient.get('/api/users/contacts')
}

export function searchContacts(keyword) {
  const k = keyword == null ? '' : String(keyword)
  // apiClient.get 第二个参数本身就是 params，不要再包一层 { params }
  return apiClient.get('/api/users/contacts/search', { keyword: k })
}

export function listFriendRequests() {
  return apiClient.get('/api/users/contacts/requests')
}

export function countFriendRequests() {
  return apiClient.get('/api/users/contacts/requests/count')
}

export function sendFriendRequest(targetUserId) {
  if (targetUserId == null) {
    return Promise.reject(new Error('targetUserId is required'))
  }
  return apiClient.post('/api/users/contacts/request', { targetUserId })
}

export function acceptFriendRequest(requestId) {
  if (requestId == null) {
    return Promise.reject(new Error('requestId is required'))
  }
  return apiClient.post('/api/users/contacts/request/accept', { requestId })
}

export function rejectFriendRequest(requestId) {
  if (requestId == null) {
    return Promise.reject(new Error('requestId is required'))
  }
  return apiClient.post('/api/users/contacts/request/reject', { requestId })
}

export function cancelFriendRequest(requestId) {
  if (requestId == null) {
    return Promise.reject(new Error('requestId is required'))
  }
  return apiClient.delete(`/api/users/contacts/request/${requestId}`)
}

export function deleteContact(contactUserId) {
  if (contactUserId == null) {
    return Promise.reject(new Error('contactUserId is required'))
  }
  return apiClient.delete(`/api/users/contacts/${contactUserId}`)
}

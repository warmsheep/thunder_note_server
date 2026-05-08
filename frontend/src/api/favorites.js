import apiClient from './client'

// D1-W8 收藏 API 封装，对齐 controller `/api/favorites`。
// 注意：add/remove 的 path 都是 messageId（不是 favorite.id），方便从消息侧直接操作。

export function listFavorites() {
  return apiClient.post('/api/favorites/list')
}

export function addFavorite(messageId) {
  if (messageId == null) {
    return Promise.reject(new Error('messageId is required'))
  }
  return apiClient.post(`/api/favorites/${messageId}`)
}

export function removeFavorite(messageId) {
  if (messageId == null) {
    return Promise.reject(new Error('messageId is required'))
  }
  return apiClient.delete(`/api/favorites/${messageId}`)
}

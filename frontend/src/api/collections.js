import apiClient from './client'

// D1-W7 合集 API 封装，对齐 controller `/api/collections`。
// 当前后端语义：合集与闪记的关联通过 `flash_notes.tags` 字符串等于 `collection.name` 实现，
// 删除合集会自动 cascadeClear 把命中的 tags 置 null（不会删闪记）。

export function listCollections() {
  return apiClient.post('/api/collections/list')
}

export function createCollection(payload) {
  // payload: { name (必填), description? }
  return apiClient.post('/api/collections', payload)
}

export function updateCollection(id, payload) {
  if (id == null) {
    return Promise.reject(new Error('id is required'))
  }
  return apiClient.put(`/api/collections/${id}`, payload || {})
}

export function deleteCollection(id) {
  if (id == null) {
    return Promise.reject(new Error('id is required'))
  }
  return apiClient.delete(`/api/collections/${id}`)
}

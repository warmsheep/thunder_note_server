import apiClient from './client'

// D1-W6 闪记消息 API 封装。
// 对齐 controller `/api/messages`：
// - list 是 POST（不是 GET），body 含 flashNoteId / page / limit
// - send 直接 POST 到 /api/messages，body 字段最小化（service 层会用 Authentication 填 senderId）
// - 收集箱使用 flashNoteId=-1（与 W5-02 的 inbox=true 字段对应同一虚拟会话）

export function listMessages({ flashNoteId, page = 1, limit = 30, peerUserId } = {}) {
  if (flashNoteId == null) {
    return Promise.reject(new Error('flashNoteId is required'))
  }
  return apiClient.post('/api/messages/list', {
    flashNoteId,
    peerUserId: peerUserId || null,
    page,
    limit
  })
}

export function sendMessage({ flashNoteId, content, clientRequestId, role = 'user' } = {}) {
  if (flashNoteId == null) {
    return Promise.reject(new Error('flashNoteId is required'))
  }
  return apiClient.post('/api/messages', {
    flashNoteId,
    content: content == null ? '' : String(content),
    clientRequestId: clientRequestId || null,
    role
  })
}

export function deleteMessage(id) {
  if (id == null) {
    return Promise.reject(new Error('id is required'))
  }
  return apiClient.delete(`/api/messages/${id}`)
}

export function deleteMessagesBatch(ids) {
  if (!Array.isArray(ids) || ids.length === 0) {
    return Promise.reject(new Error('ids must be a non-empty array'))
  }
  return apiClient.post('/api/messages/delete-batch', { ids })
}

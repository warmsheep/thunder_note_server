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

export function sendMessage({
  flashNoteId,
  content,
  clientRequestId,
  role = 'user',
  mediaType = null,
  mediaUrl = null,
  fileName = null,
  fileSize = null,
  mediaDuration = null,
  thumbnailUrl = null
} = {}) {
  if (flashNoteId == null) {
    return Promise.reject(new Error('flashNoteId is required'))
  }
  const body = {
    flashNoteId,
    content: content == null ? '' : String(content),
    clientRequestId: clientRequestId || null,
    role
  }
  // 仅在有媒体时附带媒体字段，避免对纯文本消息引入冗余 null
  if (mediaType) {
    body.mediaType = mediaType
    body.mediaUrl = mediaUrl
    body.fileName = fileName
    body.fileSize = fileSize
    body.mediaDuration = mediaDuration
    body.thumbnailUrl = thumbnailUrl
  }
  return apiClient.post('/api/messages', body)
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

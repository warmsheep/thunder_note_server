import apiClient from './client'

// D1-W6 闪记消息 API 封装。
// 对齐 controller `/api/messages`：
// - list 是 POST（不是 GET），body 含 flashNoteId / page / limit
// - send 直接 POST 到 /api/messages，body 字段最小化（service 层会用 Authentication 填 senderId）
// - 收集箱使用 flashNoteId=-1（与 W5-02 的 inbox=true 字段对应同一虚拟会话）

// D1-W20-03 改造：支持 flashNoteId 与 peerUserId 二选一
//   - flashNoteId 模式：拉取指定闪记会话的消息（含收集箱 -1）
//   - peerUserId 模式：拉取与某联系人 1v1 对话的消息
export function listMessages({ flashNoteId = null, peerUserId = null, page = 1, limit = 30 } = {}) {
  if (flashNoteId == null && peerUserId == null) {
    return Promise.reject(new Error('flashNoteId or peerUserId is required'))
  }
  return apiClient.post('/api/messages/list', {
    flashNoteId,
    peerUserId,
    page,
    limit
  })
}

// D1-W20-03 改造：支持 flashNoteId 与 receiverId 二选一
//   - flashNoteId 模式：发到指定闪记会话（含收集箱 -1）
//   - receiverId 模式：1v1 联系人对话（与 receiverId 用户的对话）
//   - 二者必须二选一，否则 reject
export function sendMessage({
  flashNoteId = null,
  receiverId = null,
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
  if (flashNoteId == null && receiverId == null) {
    return Promise.reject(new Error('flashNoteId or receiverId is required'))
  }
  const body = {
    content: content == null ? '' : String(content),
    clientRequestId: clientRequestId || null,
    role
  }
  if (flashNoteId != null) {
    body.flashNoteId = flashNoteId
  } else {
    body.receiverId = receiverId
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

// D1-W16-01 清空收集箱：仅清空 flashNoteId=-1 且 sender=receiver=self 的消息
// 后端无需任何参数，认证用户由 token 决定
export function clearInbox() {
  return apiClient.delete('/api/messages/clear-inbox')
}

// D1-W16-02 消息总数：当前用户参与的所有消息数（sender 或 receiver 为 self）
// 不是按会话计数，是全局统计
export function countMessages() {
  return apiClient.get('/api/messages/count')
}

// D1-W17-01 合并多条消息为卡片消息
// 后端约束：
//   - title 必填（非空白）
//   - messageIds 1-50 条，且必须属于同一会话
//   - flashNoteId / receiverId 二选一必填
//   - 原消息不删除，新生成 mediaType=COMPOSITE 的卡片消息
export function mergeMessages({ title, messageIds, flashNoteId = null, receiverId = null } = {}) {
  if (!title || !String(title).trim()) {
    return Promise.reject(new Error('title is required'))
  }
  if (!Array.isArray(messageIds) || messageIds.length === 0) {
    return Promise.reject(new Error('messageIds must be a non-empty array'))
  }
  if (messageIds.length > 50) {
    return Promise.reject(new Error('messageIds size must be <= 50'))
  }
  if (flashNoteId == null && receiverId == null) {
    return Promise.reject(new Error('flashNoteId or receiverId is required'))
  }
  return apiClient.post('/api/messages/merge', {
    title: String(title).trim(),
    messageIds,
    flashNoteId,
    receiverId
  })
}

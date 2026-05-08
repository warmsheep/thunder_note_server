// D1-W6 消息辅助纯函数

// 收集箱固定 flashNoteId
export const INBOX_FLASH_NOTE_ID = -1

export function isInboxFlashNoteId(value) {
  return Number(value) === INBOX_FLASH_NOTE_ID
}

// 判断当前用户是否是消息的发送者（用于气泡左/右对齐）
export function isOwnMessage(message, currentUserId) {
  if (!message || currentUserId == null) return false
  return message.senderId != null && Number(message.senderId) === Number(currentUserId)
}

// 比较 createdAt（服务端字符串），缺失视为最早；返回升序排序数。
export function compareByCreatedAtAsc(a, b) {
  const ta = a && a.createdAt ? Date.parse(a.createdAt) : 0
  const tb = b && b.createdAt ? Date.parse(b.createdAt) : 0
  if (ta !== tb) return ta - tb
  // 时间相同时按 id 升序（id 缺失放最后）
  const ia = a && a.id != null ? Number(a.id) : Number.POSITIVE_INFINITY
  const ib = b && b.id != null ? Number(b.id) : Number.POSITIVE_INFINITY
  return ia - ib
}

// 把后端 IPage records 标准化成升序数组（最新在末尾），调用方 concat 时 prepend 老消息。
export function normalizePageRecords(records) {
  if (!Array.isArray(records)) return []
  return records.slice().sort(compareByCreatedAtAsc)
}

// 生成 client request id；优先 crypto.randomUUID，否则 fallback。
export function newClientRequestId() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  // 兜底：不一定唯一但碰撞概率很低
  return `cr-${Date.now()}-${Math.floor(Math.random() * 1e9).toString(36)}`
}

// 创建 optimistic 消息：status='pending'，等 server 返回后被替换。
export function createOptimisticMessage({
  flashNoteId,
  content,
  clientRequestId,
  currentUserId,
  role = 'user',
  mediaType = null,
  mediaUrl = null,
  fileName = null,
  fileSize = null,
  mediaDuration = null,
  thumbnailUrl = null
}) {
  return {
    id: null,
    senderId: currentUserId,
    receiverId: null,
    flashNoteId,
    content: content || '',
    role,
    createdAt: new Date().toISOString(),
    clientRequestId,
    payload: null,
    mediaType,
    mediaUrl,
    fileName,
    fileSize,
    mediaDuration,
    thumbnailUrl,
    __status: 'pending',
    __local: true
  }
}

// 合并历史页：把更老的消息 prepend 到 messages 前；按 createdAt 升序去重。
export function mergeOlderRecords(messages, olderRecords) {
  const olderSorted = normalizePageRecords(olderRecords)
  const seen = new Set()
  const merged = []
  for (const list of [olderSorted, messages]) {
    for (const m of list) {
      if (!m) continue
      const key = m.id != null ? `id:${m.id}` : m.clientRequestId ? `cr:${m.clientRequestId}` : null
      if (key && seen.has(key)) continue
      if (key) seen.add(key)
      merged.push(m)
    }
  }
  return merged
}

// 把首页 records 直接当作完整 messages 列表（升序）。
export function buildInitialMessages(records) {
  return normalizePageRecords(records)
}

// 用 server 消息替换 optimistic 消息（按 clientRequestId 匹配）；找不到时 append。
export function replaceOptimisticByClientId(messages, serverMessage) {
  if (!serverMessage) return messages
  const cr = serverMessage.clientRequestId
  if (cr) {
    const idx = messages.findIndex((m) => m && m.__local && m.clientRequestId === cr)
    if (idx >= 0) {
      const next = messages.slice()
      next[idx] = serverMessage
      return next
    }
  }
  // 也可能是其他渠道的服务端消息，按 id 去重 append
  if (serverMessage.id != null && messages.some((m) => m && m.id === serverMessage.id)) {
    return messages
  }
  return [...messages, serverMessage]
}

// D1-W6 消息辅助纯函数

// 收集箱固定 flashNoteId
export const INBOX_FLASH_NOTE_ID = -1

// 后端为媒体消息自动写入的占位 content（参见 common/constant/MediaType.java）。
// 这些占位仅用于在闪记列表 / 收藏列表的"摘要"位置展示，
// 真正的消息气泡 / 媒体卡片不应再把它们当作 caption 显示，否则会出现
// 「图片缩略图 + 下方又来一行 [图片]」的双重标签。
const MEDIA_PLACEHOLDER_PATTERN = /^\s*\[(图片|视频|语音|音频|文件|附件|卡片消息)\]\s*$/

export function isMediaPlaceholderContent(content) {
  if (content == null) return false
  return MEDIA_PLACEHOLDER_PATTERN.test(String(content))
}

// 在媒体气泡 / 收藏卡片场景下取真正的 caption：占位文本 → 空字符串
export function captionForMediaContent(content) {
  if (content == null) return ''
  const s = String(content).trim()
  if (!s) return ''
  if (isMediaPlaceholderContent(s)) return ''
  return s
}

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
// D1-W20-03 改造：支持联系人 1v1 模式
//   - flashNoteId 模式：flashNoteId 必填，receiverId 仍为 null（后端按当前用户填）
//   - peerUserId 模式：peerUserId 必填，flashNoteId 留空；optimistic 上 receiverId=peerUserId
//     便于 isOwnMessage / mine 判定（mine 看 senderId === currentUserId 即可，
//     receiverId 仅用于 1v1 视觉判断和后续过滤）
export function createOptimisticMessage({
  flashNoteId = null,
  peerUserId = null,
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
    receiverId: peerUserId != null ? Number(peerUserId) : null,
    flashNoteId: flashNoteId != null ? Number(flashNoteId) : null,
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

// D1-W24-05 ChatView 草稿持久化
//
// 与 useChatScroll 同款 conversationKey 模式：
//   - flash 模式 → key = 'fn:<flashNoteId>'
//   - peer 模式 → key = 'peer:<peerUserId>'
//
// 行为：
//   - load(key, scope)：从 sessionStorage 读出未发送的文本（无则返回 ''）
//   - save(key, text, scope)：把 text 写到 sessionStorage；空字符串自动清除
//   - clear(key, scope)：删除指定会话的草稿
//
// 设计原则：
//   - 只用 sessionStorage（不跨 tab、不跨会话登录）
//   - 不做防抖：调用方决定何时写（通常是路由切换前 / 组件卸载前）
//   - 容错：sessionStorage 不可用（隐私模式）时静默退化为内存 Map

const PREFIX = 'tn:chat:draft:'
const memoryFallback = new Map()

function hasSessionStorage() {
  if (typeof window === 'undefined') return false
  try {
    const k = '__tn_probe__'
    window.sessionStorage.setItem(k, '1')
    window.sessionStorage.removeItem(k)
    return true
  } catch (_e) {
    return false
  }
}

function fullKey(conversationKey, scope = 'anon') {
  if (!conversationKey) return null
  return PREFIX + String(scope) + ':' + String(conversationKey)
}

export function loadDraft(conversationKey, scope = 'anon') {
  const key = fullKey(conversationKey, scope)
  if (!key) return ''
  if (hasSessionStorage()) {
    try {
      return window.sessionStorage.getItem(key) || ''
    } catch (_e) {
      // ignore
    }
  }
  return memoryFallback.get(key) || ''
}

export function saveDraft(conversationKey, text, scope = 'anon') {
  const key = fullKey(conversationKey, scope)
  if (!key) return
  const value = text == null ? '' : String(text)
  // 空草稿等同删除，避免 sessionStorage 累计大量空键
  if (!value) {
    clearDraft(conversationKey, scope)
    return
  }
  if (hasSessionStorage()) {
    try {
      window.sessionStorage.setItem(key, value)
      return
    } catch (_e) {
      // 写失败回退内存
    }
  }
  memoryFallback.set(key, value)
}

export function clearDraft(conversationKey, scope = 'anon') {
  const key = fullKey(conversationKey, scope)
  if (!key) return
  if (hasSessionStorage()) {
    try {
      window.sessionStorage.removeItem(key)
    } catch (_e) {
      // ignore
    }
  }
  memoryFallback.delete(key)
}

// 测试辅助：清空内存 fallback（仅用于单测隔离）
export function __resetChatDraftForTests() {
  memoryFallback.clear()
}

export const __DRAFT_PREFIX = PREFIX

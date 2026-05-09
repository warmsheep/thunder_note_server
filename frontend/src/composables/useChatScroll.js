import { ref, watch, nextTick } from 'vue'

// D1-W19 ChatView 滚动行为统一抽象。
//
// 这个 composable 把"发送 / 加载历史 / 新消息到达 / 离开会话 / 进入会话"五个
// 时机的滚动行为收口在一处，避免分散在 ChatView 的多个 handler 里互相打架。
//
// 行为约定（对齐 W19 验收标准）：
// 1) loadMore 时（接近顶部）：先记录 scrollHeight，加载完成后用差值还原 scrollTop，
//    保证视觉位置不跳。
// 2) 发送后：强制平滑滚到底部（用户主动操作的反馈）。
// 3) 新消息从尾部追加：
//    - 用户当前在底部（distance < 阈值）→ 自动跟随到底
//    - 用户已上滑查看历史 → 不打扰，仅 hasNewBelow=true 让"回到底部"按钮显示新消息提示
// 4) 离开会话：rememberScroll 把 scrollTop 写到 sessionStorage（按 flashNoteId 隔离）
// 5) 进入会话：restoreScrollOrBottom 优先恢复 sessionStorage 位置，没有则滚到底
//
// 注意：composable 不引入对 Pinia store 的硬依赖，外部传 messages（ref 或 getter）
// 与 shouldLoadMore / onLoadMore 闭包，方便单元测试。

const SESSION_KEY_PREFIX = 'tn:chat:scroll:'
const BOTTOM_THRESHOLD = 80
const LOAD_MORE_THRESHOLD = 80

// D1-W20-04 conversationKey 按模式隔离：
//   - 闪记会话：`fn:7` / `fn:-1`（收集箱）
//   - 联系人 1v1：`peer:42`
// sessionStorage key 直接拼接该字符串，flash / peer 互不侵蚀
function sessionKey(conversationKey) {
  return `${SESSION_KEY_PREFIX}${conversationKey}`
}

// D1-W28-06 reversed 模式（微博风）滑动语义反转：
//   - 不抱 reversed 时（IM 默认）：
//       * 最新在顶部；DOM 顺序 [最旧 → 最新]
//       * scrollTop 近 0 → loadMore（看更老）
//       * append 后跳到 scrollHeight（贴底）
//       * 「isAtBottom」表示贴底（靠近最新一条）
//   - reversed=true 时（微博风）：
//       * DOM 顺序 仍为 [最旧 → 最新]（store 不动），CSS flex-direction: column-reverse 让最新在视觉顶部
//       * 浏览器下颚反转后，scrollTop=0 仍表示「视觉顶部」（看最新）
//       * 但「看更老消息」是往下滑，所以 loadMore 触发在 distanceToBottom < threshold
//       * append 后不需要主动滑动（column-reverse 会自动保持视觉顶部贴顶）。如果用户在往下看老消息，留 hasNewBelow=true 提示

function bottomKeyOf(arr) {
  if (!Array.isArray(arr) || arr.length === 0) return null
  const last = arr[arr.length - 1]
  if (!last) return null
  if (last.id != null) return `id:${last.id}`
  if (last.clientRequestId) return `cr:${last.clientRequestId}`
  return `idx:${arr.length - 1}`
}

// D1-W20-04 构造参数 flashNoteId 重命名为 conversationKey（可以是任意字符串或 ref/getter）。
// 同时保留 flashNoteId 别名参数作为向后兼容（旧调用传数字仍能工作，会被堆叠转成字符串作为 key）。
export function useChatScroll({
  conversationKey,
  flashNoteId, // 兼容：仅当 conversationKey 未传时起作用
  messages,
  shouldLoadMore = () => false,
  onLoadMore = null,
  bottomThreshold = BOTTOM_THRESHOLD,
  loadMoreThreshold = LOAD_MORE_THRESHOLD,
  // D1-W28-06 reversed 可以传 ref / getter / boolean，在运行期读取当前值
  reversed = false
} = {}) {
  function getReversed() {
    if (reversed == null) return false
    if (typeof reversed === 'function') return Boolean(reversed())
    if (typeof reversed === 'object' && 'value' in reversed) return Boolean(reversed.value)
    return Boolean(reversed)
  }
  const scrollerRef = ref(null)
  const isAtBottom = ref(true)
  const hasNewBelow = ref(false)

  let lastLength = 0
  let lastBottomKey = null

  // 最终使用的身份源：优先 conversationKey，没传则退回到 flashNoteId
  const keySource = conversationKey != null ? conversationKey : flashNoteId

  function getMessages() {
    if (!messages) return []
    if (typeof messages === 'function') return messages() || []
    if ('value' in messages) return messages.value || []
    return messages
  }

  function getConversationKey() {
    if (keySource == null) return null
    if (typeof keySource === 'function') return keySource()
    if (keySource && typeof keySource === 'object' && 'value' in keySource) return keySource.value
    return keySource
  }

  function distanceToBottom() {
    const el = scrollerRef.value
    if (!el) return 0
    return el.scrollHeight - el.scrollTop - el.clientHeight
  }

  // 「贴底/贴顶」靠近最新消息的判定：
  //   - 默认 IM：最新在贴底，判断 distanceToBottom
  //   - reversed 微博风：CSS column-reverse 下「视觉顶部」在 scrollTop=0，判断 scrollTop 小于阈值
  function computeAtBottom() {
    const el = scrollerRef.value
    if (!el) return true
    if (getReversed()) {
      return el.scrollTop < bottomThreshold
    }
    return distanceToBottom() < bottomThreshold
  }

  // scrollToBottom 语义：「跳到最新消息位置」
  //   - IM 默认：scrollTop = scrollHeight
  //   - reversed 微博风：scrollTop = 0（视觉顶部）
  function scrollToBottom({ smooth = false } = {}) {
    const el = scrollerRef.value
    if (!el) return
    const targetTop = getReversed() ? 0 : el.scrollHeight
    if (smooth && typeof el.scrollTo === 'function') {
      try {
        el.scrollTo({ top: targetTop, behavior: 'smooth' })
      } catch (_e) {
        el.scrollTop = targetTop
      }
    } else {
      el.scrollTop = targetTop
    }
    isAtBottom.value = true
    hasNewBelow.value = false
  }

  async function handleScroll() {
    const el = scrollerRef.value
    if (!el) return
    isAtBottom.value = computeAtBottom()
    if (isAtBottom.value) {
      hasNewBelow.value = false
    }
    // loadMore 触发位置：
    //   - IM：scrollTop 靠近 0（看老消息的方向）
    //   - reversed 微博风：distanceToBottom 靠近0（往下滑看老消息）
    const isReversed = getReversed()
    const nearLoadEdge = isReversed
      ? distanceToBottom() < loadMoreThreshold
      : el.scrollTop < loadMoreThreshold
    if (
      nearLoadEdge &&
      typeof shouldLoadMore === 'function' &&
      shouldLoadMore() &&
      typeof onLoadMore === 'function'
    ) {
      const beforeHeight = el.scrollHeight
      const beforeScrollTop = el.scrollTop
      try {
        await onLoadMore()
        await nextTick()
        const after = scrollerRef.value
        if (after) {
          // 维持当前可见 anchor：
          //   IM：新 scrollTop = 旧 scrollTop + (新 scrollHeight - 旧 scrollHeight)
          //   reversed：DOM 追加到顶部（老消息）后，视觉上出现在顶部 anchor 以下，
          //   scrollTop 不需要动；column-reverse 下浏览器自动维持 scrollTop 不变。
          if (!isReversed) {
            after.scrollTop = beforeScrollTop + (after.scrollHeight - beforeHeight)
          }
        }
      } catch (_e) {
        // 错误由调用方/store 处理
      }
    }
  }

  // 监听 messages 变化：区分 prepend（loadMore）与 append（新消息）
  watch(
    () => {
      const arr = getMessages()
      return { len: arr.length, key: bottomKeyOf(arr) }
    },
    async ({ len, key }) => {
      const grew = len > lastLength
      const bottomChanged = key !== lastBottomKey
      // 末尾 key 没变但 length 增加 → 是 prepend（loadMore），保持位置不动；
      // 末尾 key 变了 + length 增加 → 是 append（新消息）；
      // 末尾 key 变了但 length 不变 → optimistic 被替换为 server 消息，不视为新增。
      if (grew && bottomChanged) {
        if (isAtBottom.value) {
          await nextTick()
          scrollToBottom({ smooth: true })
        } else {
          hasNewBelow.value = true
        }
      }
      lastLength = len
      lastBottomKey = key
    }
  )

  // 进入会话切换时（conversationKey 变化），重置内部状态。
  // 调用方需要在 store.openConversation 完成 + nextTick 后再调用 restoreScrollOrBottom。
  watch(
    () => getConversationKey(),
    () => {
      lastLength = 0
      lastBottomKey = null
      isAtBottom.value = true
      hasNewBelow.value = false
    }
  )

  function rememberScroll() {
    if (typeof sessionStorage === 'undefined') return
    const el = scrollerRef.value
    const key = getConversationKey()
    if (!el || key == null) return
    try {
      sessionStorage.setItem(
        sessionKey(key),
        JSON.stringify({
          scrollTop: el.scrollTop,
          scrollHeight: el.scrollHeight,
          clientHeight: el.clientHeight,
          atBottom: computeAtBottom(),
          savedAt: Date.now()
        })
      )
    } catch (_e) {
      // sessionStorage 可能因隐私模式 / 配额异常报错，吞掉即可
    }
  }

  function readRemembered() {
    if (typeof sessionStorage === 'undefined') return null
    const key = getConversationKey()
    if (key == null) return null
    try {
      const raw = sessionStorage.getItem(sessionKey(key))
      if (!raw) return null
      const parsed = JSON.parse(raw)
      if (!parsed || typeof parsed !== 'object') return null
      return parsed
    } catch (_e) {
      return null
    }
  }

  async function restoreScrollOrBottom() {
    await nextTick()
    const el = scrollerRef.value
    if (!el) return
    const saved = readRemembered()
    if (saved && typeof saved.scrollTop === 'number' && !saved.atBottom) {
      // 限幅在合法范围（首页可能比上次记录更短，比如对方撤回/删除）
      const maxScrollTop = Math.max(0, el.scrollHeight - el.clientHeight)
      el.scrollTop = Math.min(saved.scrollTop, maxScrollTop)
      isAtBottom.value = computeAtBottom()
      // 恢复后初始化 length / bottomKey，避免下一次 watch 把 restore 当成 append
      const arr = getMessages()
      lastLength = arr.length
      lastBottomKey = bottomKeyOf(arr)
      return
    }
    scrollToBottom({ smooth: false })
    const arr = getMessages()
    lastLength = arr.length
    lastBottomKey = bottomKeyOf(arr)
  }

  function clearRemembered() {
    if (typeof sessionStorage === 'undefined') return
    const key = getConversationKey()
    if (key == null) return
    try {
      sessionStorage.removeItem(sessionKey(key))
    } catch (_e) {
      // ignore
    }
  }

  return {
    scrollerRef,
    isAtBottom,
    hasNewBelow,
    scrollToBottom,
    handleScroll,
    rememberScroll,
    restoreScrollOrBottom,
    clearRemembered,
    // 暴露给测试用的 helpers
    __test: { distanceToBottom, computeAtBottom, sessionKey, bottomKeyOf }
  }
}

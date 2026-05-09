import { describe, it, expect, beforeEach, vi } from 'vitest'
import { ref, nextTick, createApp, h } from 'vue'
import { useChatScroll } from './useChatScroll'

// 项目无 @vue/test-utils。我们用 createApp + 一个最小 setup 宿主组件来挂载
// composable，让其内部 watch / effectScope 能正常工作，然后通过返回值调用其 API。
function runInComponent(setupFn) {
  let captured
  const app = createApp({
    setup() {
      captured = setupFn()
      return () => h('div')
    }
  })
  const root = document.createElement('div')
  app.mount(root)
  return { result: captured, unmount: () => app.unmount() }
}

// 模拟一个具有 scrollTop / scrollHeight / clientHeight 的滚动容器。
function makeScroller({ scrollHeight = 1000, clientHeight = 400, scrollTop = 0 } = {}) {
  return {
    scrollTop,
    scrollHeight,
    clientHeight,
    scrollTo: vi.fn(function (opts) {
      if (opts && typeof opts.top === 'number') this.scrollTop = opts.top
    })
  }
}

describe('useChatScroll', () => {
  beforeEach(() => {
    if (typeof sessionStorage !== 'undefined') {
      sessionStorage.clear()
    }
  })

  it('handleScroll 在接近顶部时调用 onLoadMore，并保持视觉位置 (scrollTop = scrollHeightAfter - scrollHeightBefore)', async () => {
    const flashNoteId = ref(7)
    const messages = ref([{ id: 10 }, { id: 11 }, { id: 12 }])
    const onLoadMore = vi.fn(async () => {
      // 模拟 store.loadMore：消息数组前面 prepend 老消息，导致 scrollHeight 变大
      messages.value = [{ id: 8 }, { id: 9 }, ...messages.value]
      scroller.scrollHeight = 1500 // 老消息加进来后总高度增加 500
    })
    let scroller
    const { result, unmount } = runInComponent(() =>
      useChatScroll({
        flashNoteId,
        messages,
        shouldLoadMore: () => true,
        onLoadMore
      })
    )
    scroller = makeScroller({ scrollHeight: 1000, clientHeight: 400, scrollTop: 50 })
    result.scrollerRef.value = scroller
    await result.handleScroll()
    expect(onLoadMore).toHaveBeenCalledTimes(1)
    // 视觉位置保持：50 + (1500 - 1000) = 550
    expect(scroller.scrollTop).toBe(550)
    unmount()
  })

  it('handleScroll 在已到底部时不会触发 loadMore', async () => {
    const onLoadMore = vi.fn()
    const { result, unmount } = runInComponent(() =>
      useChatScroll({
        flashNoteId: ref(7),
        messages: ref([]),
        shouldLoadMore: () => true,
        onLoadMore
      })
    )
    result.scrollerRef.value = makeScroller({ scrollHeight: 1000, clientHeight: 400, scrollTop: 600 })
    await result.handleScroll()
    expect(onLoadMore).not.toHaveBeenCalled()
    unmount()
  })

  it('append 新消息时：用户在底部 → 自动滚到底；用户上滑 → hasNewBelow=true', async () => {
    const messages = ref([{ id: 1 }])
    const { result, unmount } = runInComponent(() =>
      useChatScroll({
        flashNoteId: ref(7),
        messages,
        shouldLoadMore: () => false,
        onLoadMore: null
      })
    )

    // 用户已在底部
    const scrollerAtBottom = makeScroller({ scrollHeight: 1000, clientHeight: 400, scrollTop: 600 })
    result.scrollerRef.value = scrollerAtBottom
    await result.handleScroll() // 同步 isAtBottom=true
    expect(result.hasNewBelow.value).toBe(false)

    // 末尾追加一条新消息（id=2）
    messages.value = [...messages.value, { id: 2 }]
    await nextTick()
    await nextTick()
    expect(scrollerAtBottom.scrollTo).toHaveBeenCalled()
    expect(result.hasNewBelow.value).toBe(false)

    // 切换：用户上滑
    const scrollerAway = makeScroller({ scrollHeight: 1500, clientHeight: 400, scrollTop: 100 })
    result.scrollerRef.value = scrollerAway
    await result.handleScroll() // isAtBottom=false
    messages.value = [...messages.value, { id: 3 }]
    await nextTick()
    expect(result.hasNewBelow.value).toBe(true)

    unmount()
  })

  it('prepend 老消息（loadMore）不会被当作 append 触发跟随', async () => {
    const messages = ref([{ id: 5 }])
    const { result, unmount } = runInComponent(() =>
      useChatScroll({
        flashNoteId: ref(7),
        messages,
        shouldLoadMore: () => false,
        onLoadMore: null
      })
    )
    const scroller = makeScroller({ scrollHeight: 1000, clientHeight: 400, scrollTop: 50 })
    result.scrollerRef.value = scroller
    // 模拟 prepend：在前面加老消息，末尾 id 不变
    messages.value = [{ id: 3 }, { id: 4 }, ...messages.value]
    await nextTick()
    expect(scroller.scrollTo).not.toHaveBeenCalled()
    expect(result.hasNewBelow.value).toBe(false)
    unmount()
  })

  it('rememberScroll + restoreScrollOrBottom 通过 sessionStorage 跨进入恢复', async () => {
    const fid = ref(42)
    const messages = ref([{ id: 1 }, { id: 2 }])
    const { result, unmount } = runInComponent(() =>
      useChatScroll({
        flashNoteId: fid,
        messages,
        shouldLoadMore: () => false,
        onLoadMore: null
      })
    )
    const scroller = makeScroller({ scrollHeight: 1500, clientHeight: 400, scrollTop: 320 })
    result.scrollerRef.value = scroller
    result.rememberScroll()

    // 离开会话（unmount）
    unmount()

    // 重新进入：新一次组件挂载
    const { result: result2, unmount: unmount2 } = runInComponent(() =>
      useChatScroll({
        flashNoteId: fid,
        messages,
        shouldLoadMore: () => false,
        onLoadMore: null
      })
    )
    const scroller2 = makeScroller({ scrollHeight: 1500, clientHeight: 400, scrollTop: 0 })
    result2.scrollerRef.value = scroller2
    await result2.restoreScrollOrBottom()
    expect(scroller2.scrollTop).toBe(320)
    unmount2()
  })

  it('restoreScrollOrBottom 在没有 sessionStorage 记录或上次记录在底部时，滚到底部', async () => {
    const fid = ref(99)
    const messages = ref([{ id: 1 }])
    const { result, unmount } = runInComponent(() =>
      useChatScroll({
        flashNoteId: fid,
        messages,
        shouldLoadMore: () => false,
        onLoadMore: null
      })
    )
    const scroller = makeScroller({ scrollHeight: 1200, clientHeight: 400, scrollTop: 0 })
    result.scrollerRef.value = scroller
    await result.restoreScrollOrBottom()
    expect(scroller.scrollTop).toBe(1200) // = scrollHeight，瞬时跳到底
    unmount()
  })

  it('clearRemembered 抹掉 sessionStorage', () => {
    const fid = ref(15)
    const { result, unmount } = runInComponent(() =>
      useChatScroll({
        flashNoteId: fid,
        messages: ref([]),
        shouldLoadMore: () => false,
        onLoadMore: null
      })
    )
    const scroller = makeScroller({ scrollHeight: 1000, clientHeight: 400, scrollTop: 200 })
    result.scrollerRef.value = scroller
    result.rememberScroll()
    expect(sessionStorage.getItem('tn:chat:scroll:15')).not.toBeNull()
    result.clearRemembered()
    expect(sessionStorage.getItem('tn:chat:scroll:15')).toBeNull()
    unmount()
  })

  it('scrollToBottom 清掉 hasNewBelow 并把 scrollTop 设为 scrollHeight', () => {
    const { result, unmount } = runInComponent(() =>
      useChatScroll({
        flashNoteId: ref(1),
        messages: ref([]),
        shouldLoadMore: () => false,
        onLoadMore: null
      })
    )
    const scroller = makeScroller({ scrollHeight: 800, clientHeight: 400, scrollTop: 0 })
    result.scrollerRef.value = scroller
    result.hasNewBelow.value = true
    result.scrollToBottom({ smooth: false })
    expect(scroller.scrollTop).toBe(800)
    expect(result.hasNewBelow.value).toBe(false)
    expect(result.isAtBottom.value).toBe(true)
    unmount()
  })

  // D1-W20-04 conversationKey 按模式隔离
  describe('conversationKey (D1-W20)', () => {
    it('显式传 conversationKey 时，sessionStorage key 是该字符串', () => {
      const { result, unmount } = runInComponent(() =>
        useChatScroll({
          conversationKey: ref('peer:42'),
          messages: ref([]),
          shouldLoadMore: () => false,
          onLoadMore: null
        })
      )
      const scroller = makeScroller({ scrollHeight: 1000, clientHeight: 400, scrollTop: 200 })
      result.scrollerRef.value = scroller
      result.rememberScroll()
      expect(sessionStorage.getItem('tn:chat:scroll:peer:42')).not.toBeNull()
      // 不会污染 fn 命名空间
      expect(sessionStorage.getItem('tn:chat:scroll:fn:42')).toBeNull()
      result.clearRemembered()
      expect(sessionStorage.getItem('tn:chat:scroll:peer:42')).toBeNull()
      unmount()
    })

    it('flash 与 peer 用相同数字 ID 不会互相污染', () => {
      // 实例 A：闪记会话 fn:7
      const { result: rA, unmount: uA } = runInComponent(() =>
        useChatScroll({
          conversationKey: ref('fn:7'),
          messages: ref([]),
          shouldLoadMore: () => false
        })
      )
      rA.scrollerRef.value = makeScroller({ scrollHeight: 1000, clientHeight: 400, scrollTop: 200 })
      rA.rememberScroll()
      uA()

      // 实例 B：联系人 1v1 peer:7（同一数字 ID 但不同模式）
      const { result: rB, unmount: uB } = runInComponent(() =>
        useChatScroll({
          conversationKey: ref('peer:7'),
          messages: ref([{ id: 1 }]),
          shouldLoadMore: () => false
        })
      )
      const scrollerB = makeScroller({ scrollHeight: 1500, clientHeight: 400, scrollTop: 0 })
      rB.scrollerRef.value = scrollerB
      // restoreScrollOrBottom 在 peer:7 下应当走 "没有记录 → 滚到底"，
      // 而不是误读 fn:7 的位置
      return rB.restoreScrollOrBottom().then(() => {
        expect(scrollerB.scrollTop).toBe(1500) // 滚到底
        uB()
      })
    })
  })
})

// happy-dom 默认就支持 sessionStorage，不需要额外 setup。

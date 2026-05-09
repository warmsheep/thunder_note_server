import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { createApp, h, nextTick } from 'vue'
import SplashOverlay from './SplashOverlay.vue'

// D1-W26-04 SplashOverlay 行为测试
//
// 验证：
//   - 桌面端（matchMedia 不匹配 max-width: 768）→ 不渲染
//   - 移动端首次访问 → 渲染遮罩，500ms 后开始 fading，再 220ms 后销毁
//   - 同一会话第二次挂载 → 不渲染（sessionStorage 标志位）

function mountIntoBody(component) {
  const root = document.createElement('div')
  document.body.appendChild(root)
  const app = createApp({ render: () => h(component) })
  app.mount(root)
  return {
    root,
    unmount: () => {
      app.unmount()
      root.remove()
    }
  }
}

function setMatchMedia(matchesPredicate) {
  Object.defineProperty(window, 'matchMedia', {
    configurable: true,
    writable: true,
    value: (query) => ({
      matches: matchesPredicate(query),
      media: query,
      addEventListener() {},
      removeEventListener() {},
      addListener() {},
      removeListener() {},
      onchange: null,
      dispatchEvent: () => false
    })
  })
}

describe('SplashOverlay', () => {
  beforeEach(() => {
    sessionStorage.clear()
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
    document.body.innerHTML = ''
  })

  it('桌面端（max-width: 768 不匹配）→ 不渲染遮罩', async () => {
    setMatchMedia(() => false)
    const ctx = mountIntoBody(SplashOverlay)
    await nextTick()
    expect(ctx.root.querySelector('.splash-overlay')).toBeNull()
    ctx.unmount()
  })

  it('移动端首次挂载 → 渲染遮罩，500ms 后进入 fading，再 220ms 后销毁', async () => {
    setMatchMedia((q) => q.includes('max-width: 768'))
    const ctx = mountIntoBody(SplashOverlay)

    // onMounted 同步给 visible.value = true，下一帧 DOM 渲染遮罩
    await nextTick()
    expect(ctx.root.querySelector('.splash-overlay')).not.toBeNull()
    expect(ctx.root.querySelector('.splash-overlay.fading')).toBeNull()

    // 500ms 后触发 fading
    vi.advanceTimersByTime(500)
    await nextTick()
    expect(ctx.root.querySelector('.splash-overlay.fading')).not.toBeNull()

    // 再 220ms 整个销毁
    vi.advanceTimersByTime(220)
    await nextTick()
    expect(ctx.root.querySelector('.splash-overlay')).toBeNull()

    // sessionStorage 已写标志位
    expect(sessionStorage.getItem('tn:splash:shown')).toBe('1')

    ctx.unmount()
  })

  it('同一会话第二次挂载 → 跳过显示（sessionStorage 标志位生效）', async () => {
    setMatchMedia((q) => q.includes('max-width: 768'))
    sessionStorage.setItem('tn:splash:shown', '1')

    const ctx = mountIntoBody(SplashOverlay)
    await nextTick()
    expect(ctx.root.querySelector('.splash-overlay')).toBeNull()
    ctx.unmount()
  })

  it('包含 ⚡ logo + 应用名 + slogan（与 Android `about_slogan` 对齐）', async () => {
    setMatchMedia((q) => q.includes('max-width: 768'))
    const ctx = mountIntoBody(SplashOverlay)
    await nextTick()

    const root = ctx.root
    expect(root.querySelector('.splash-logo')?.textContent).toBe('⚡')
    expect(root.querySelector('.splash-name')?.textContent).toBe('闪记')
    const slogan = root.querySelector('.splash-slogan')
    expect(slogan).not.toBeNull()
    expect(slogan.textContent).toContain('快速记录，灵感闪现')
    expect(slogan.textContent).toContain('让笔记像闪电一样快捷')

    ctx.unmount()
  })
})

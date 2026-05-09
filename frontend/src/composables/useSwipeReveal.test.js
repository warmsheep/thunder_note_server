import { describe, it, expect } from 'vitest'
import { createApp, h } from 'vue'
import { useSwipeReveal } from './useSwipeReveal'

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

function touchEvent(clientX) {
  return {
    touches: [{ clientX }],
    changedTouches: [{ clientX }]
  }
}

describe('useSwipeReveal', () => {
  it('左滑达到阈值后保持展开', () => {
    const { result, unmount } = runInComponent(() => useSwipeReveal())
    result.begin('n-1', touchEvent(240), 200)
    result.move('n-1', touchEvent(140))
    expect(result.offsetOf('n-1')).toBe(-96)
    expect(result.end('n-1')).toBe(true)
    expect(result.isOpen('n-1')).toBe(true)
    unmount()
  })

  it('滑动不足阈值时自动回弹关闭', () => {
    const { result, unmount } = runInComponent(() => useSwipeReveal())
    result.begin('n-2', touchEvent(200), 320)
    result.move('n-2', touchEvent(150))
    expect(result.offsetOf('n-2')).toBe(-50)
    expect(result.end('n-2')).toBe(false)
    expect(result.isOpen('n-2')).toBe(false)
    unmount()
  })

  it('开始滑动新条目时会关闭旧条目的展开状态', () => {
    const { result, unmount } = runInComponent(() => useSwipeReveal())
    result.begin('a', touchEvent(240), 200)
    result.move('a', touchEvent(120))
    result.end('a')
    expect(result.isOpen('a')).toBe(true)

    result.begin('b', touchEvent(240), 200)
    expect(result.isOpen('a')).toBe(false)
    result.close()
    unmount()
  })
})

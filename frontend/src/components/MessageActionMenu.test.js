import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { ref, createApp, h, nextTick } from 'vue'
import MessageActionMenu from './MessageActionMenu.vue'

// D1-W21-01 MessageActionMenu 组件测试
//
// 不引入 @vue/test-utils；用最小 createApp 宿主装载组件，
// 通过外部 ref 控制 props 与监听 emit 事件。

function mountMenu(initialProps = {}) {
  const open = ref(initialProps.open ?? true)
  const x = ref(initialProps.x ?? 100)
  const y = ref(initialProps.y ?? 100)
  const items = ref(
    initialProps.items ?? [
      { key: 'copy', label: '复制', icon: '📋' },
      { key: 'forward', label: '转发', icon: '↪' },
      { key: 'delete', label: '删除', icon: '🗑', danger: true }
    ]
  )
  const onSelect = vi.fn()
  const onUpdateOpen = vi.fn((v) => (open.value = v))

  const root = document.createElement('div')
  document.body.appendChild(root)
  const app = createApp({
    setup() {
      return () =>
        h(MessageActionMenu, {
          open: open.value,
          x: x.value,
          y: y.value,
          items: items.value,
          'onUpdate:open': onUpdateOpen,
          onSelect: onSelect
        })
    }
  })
  app.mount(root)
  return {
    open,
    x,
    y,
    items,
    onSelect,
    onUpdateOpen,
    unmount: () => {
      app.unmount()
      root.remove()
    }
  }
}

function findMenuEl() {
  return document.body.querySelector('.action-menu')
}

describe('MessageActionMenu', () => {
  beforeEach(() => {
    // 给一个稳定的视口尺寸
    Object.defineProperty(window, 'innerWidth', { value: 1024, configurable: true })
    Object.defineProperty(window, 'innerHeight', { value: 768, configurable: true })
  })
  afterEach(() => {
    // 清理可能残留的菜单
    document.body.innerHTML = ''
  })

  it('open=true 时挂到 body，open=false 时移除', async () => {
    const ctx = mountMenu({ open: true })
    await nextTick()
    expect(findMenuEl()).not.toBeNull()
    ctx.open.value = false
    await nextTick()
    expect(findMenuEl()).toBeNull()
    ctx.unmount()
  })

  it('点击 menu item 触发 select(key) 并请求关闭', async () => {
    const ctx = mountMenu({ open: true })
    await nextTick()
    const items = document.body.querySelectorAll('.action-menu-item')
    expect(items.length).toBe(3)
    items[1].dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await nextTick()
    expect(ctx.onSelect).toHaveBeenCalledWith('forward')
    expect(ctx.onUpdateOpen).toHaveBeenCalledWith(false)
    ctx.unmount()
  })

  it('disabled 项点击不触发 select', async () => {
    const ctx = mountMenu({
      open: true,
      items: [
        { key: 'a', label: 'A' },
        { key: 'b', label: 'B', disabled: true }
      ]
    })
    await nextTick()
    const items = document.body.querySelectorAll('.action-menu-item')
    items[1].dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await nextTick()
    expect(ctx.onSelect).not.toHaveBeenCalled()
    ctx.unmount()
  })

  it('ESC 键请求关闭', async () => {
    const ctx = mountMenu({ open: true })
    // 给组件足够时间绑监听（watch immediate + 双 nextTick）
    await nextTick()
    await nextTick()
    await nextTick()
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    expect(ctx.onUpdateOpen).toHaveBeenCalledWith(false)
    ctx.unmount()
  })

  it('点击菜单外空白请求关闭', async () => {
    const ctx = mountMenu({ open: true })
    await nextTick()
    await nextTick()
    await nextTick()
    // 触发一个不在菜单元素内的 mousedown
    const outside = document.createElement('div')
    document.body.appendChild(outside)
    outside.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    expect(ctx.onUpdateOpen).toHaveBeenCalledWith(false)
    ctx.unmount()
  })

  it('点击菜单内部不会请求关闭', async () => {
    const ctx = mountMenu({ open: true })
    await nextTick()
    await nextTick()
    await nextTick()
    const menu = findMenuEl()
    expect(menu).not.toBeNull()
    menu.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    expect(ctx.onUpdateOpen).not.toHaveBeenCalled()
    ctx.unmount()
  })

  it('坐标超过视口右下时自动反翻向左/上展开', async () => {
    const ctx = mountMenu({ open: true, x: 1020, y: 760 })
    await nextTick()
    const menu = findMenuEl()
    const left = parseInt(menu.style.left, 10)
    const top = parseInt(menu.style.top, 10)
    expect(left).toBeLessThan(1020) // 翻向左
    expect(top).toBeLessThan(760) // 翻向上
    ctx.unmount()
  })
})

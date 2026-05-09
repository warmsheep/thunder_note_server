import { describe, it, expect, vi, afterEach } from 'vitest'
import { ref, createApp, h, nextTick } from 'vue'
import QuickCaptureDialog from './QuickCaptureDialog.vue'

// D1-W22-02 QuickCaptureDialog 测试

function mountDialog(initialOpen = true) {
  const open = ref(initialOpen)
  const busy = ref(false)
  const submitArg = ref(null)
  const cancelCalled = ref(false)
  const root = document.createElement('div')
  document.body.appendChild(root)
  const app = createApp({
    setup() {
      return () =>
        h(QuickCaptureDialog, {
          open: open.value,
          busy: busy.value,
          'onUpdate:open': (v) => (open.value = v),
          onSubmit: (text) => (submitArg.value = text),
          onCancel: () => (cancelCalled.value = true)
        })
    }
  })
  app.mount(root)
  return {
    root,
    open,
    busy,
    submitArg,
    cancelCalled,
    unmount: () => {
      app.unmount()
      root.remove()
    }
  }
}

describe('QuickCaptureDialog', () => {
  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('open=true 时挂载并显示对话框', async () => {
    const ctx = mountDialog(true)
    await nextTick()
    expect(document.querySelector('.modal')).not.toBeNull()
    ctx.unmount()
  })

  it('open=false 时不渲染对话框', async () => {
    const ctx = mountDialog(false)
    await nextTick()
    expect(document.querySelector('.modal')).toBeNull()
    ctx.unmount()
  })

  it('canSend 默认 false（无文本）；输入后开启发送按钮', async () => {
    const ctx = mountDialog(true)
    await nextTick()
    const btn = document.querySelector('.btn-primary')
    expect(btn.disabled).toBe(true)
    const ta = document.querySelector('.quick-textarea')
    ta.value = 'hello'
    ta.dispatchEvent(new Event('input', { bubbles: true }))
    await nextTick()
    expect(btn.disabled).toBe(false)
    ctx.unmount()
  })

  it('Cmd+Enter 触发 submit；ESC 触发 cancel', async () => {
    const ctx = mountDialog(true)
    await nextTick()
    const ta = document.querySelector('.quick-textarea')
    ta.value = 'hi'
    ta.dispatchEvent(new Event('input', { bubbles: true }))
    await nextTick()
    // 模拟 Cmd+Enter
    ta.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', metaKey: true, bubbles: true }))
    await nextTick()
    expect(ctx.submitArg.value).toBe('hi')

    // ESC
    ta.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await nextTick()
    expect(ctx.cancelCalled.value).toBe(true)
    expect(ctx.open.value).toBe(false)
    ctx.unmount()
  })

  it('busy=true 时取消按钮被禁用，关闭按钮被禁用', async () => {
    const ctx = mountDialog(true)
    ctx.busy.value = true
    await nextTick()
    const cancelBtn = document.querySelector('.btn-secondary')
    expect(cancelBtn.disabled).toBe(true)
    const closeBtn = document.querySelector('.modal-close')
    expect(closeBtn.disabled).toBe(true)
    ctx.unmount()
  })
})

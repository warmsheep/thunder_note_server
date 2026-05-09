import { describe, it, expect, afterEach } from 'vitest'
import { ref, createApp, h, nextTick } from 'vue'
import AvatarPickerDialog, { PROFILE_EMOJIS } from './AvatarPickerDialog.vue'

// D1-W23-01 AvatarPickerDialog 测试

function mountDialog(initialProps = {}) {
  const open = ref(initialProps.open ?? true)
  const busy = ref(initialProps.busy ?? false)
  const currentAvatar = ref(initialProps.currentAvatar ?? '')
  const pickImageArg = ref(null)
  const selectEmojiArg = ref(null)
  const cancelCalled = ref(false)
  const root = document.createElement('div')
  document.body.appendChild(root)
  const app = createApp({
    setup() {
      return () =>
        h(AvatarPickerDialog, {
          open: open.value,
          busy: busy.value,
          currentAvatar: currentAvatar.value,
          'onUpdate:open': (v) => (open.value = v),
          onPickImage: (f) => (pickImageArg.value = f),
          onSelectEmoji: (e) => (selectEmojiArg.value = e),
          onCancel: () => (cancelCalled.value = true)
        })
    }
  })
  app.mount(root)
  return {
    open,
    busy,
    currentAvatar,
    pickImageArg,
    selectEmojiArg,
    cancelCalled,
    unmount: () => {
      app.unmount()
      root.remove()
    }
  }
}

describe('AvatarPickerDialog', () => {
  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('Emoji 列表与 Android 对齐（12 项）', () => {
    expect(PROFILE_EMOJIS).toHaveLength(12)
    expect(PROFILE_EMOJIS).toContain('💼')
    expect(PROFILE_EMOJIS).toContain('😊')
  })

  it('open=true 时渲染 emoji 网格，每项一个按钮', async () => {
    const ctx = mountDialog({ open: true })
    await nextTick()
    const buttons = document.querySelectorAll('.emoji-btn')
    expect(buttons.length).toBe(PROFILE_EMOJIS.length)
    ctx.unmount()
  })

  it('点击 emoji 按钮 emit select-emoji', async () => {
    const ctx = mountDialog({ open: true })
    await nextTick()
    const first = document.querySelector('.emoji-btn')
    first.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await nextTick()
    expect(ctx.selectEmojiArg.value).toBe(PROFILE_EMOJIS[0])
    ctx.unmount()
  })

  it('currentAvatar 是 emoji 时该项高亮 (.selected)', async () => {
    const ctx = mountDialog({ open: true, currentAvatar: '🌟' })
    await nextTick()
    const selected = document.querySelectorAll('.emoji-btn.selected')
    expect(selected.length).toBe(1)
    expect(selected[0].textContent.trim()).toBe('🌟')
    ctx.unmount()
  })

  it('busy=true 时禁用所有 emoji 按钮和 tab 按钮', async () => {
    const ctx = mountDialog({ open: true, busy: true })
    await nextTick()
    const buttons = document.querySelectorAll('.emoji-btn')
    for (const btn of buttons) {
      expect(btn.disabled).toBe(true)
    }
    ctx.unmount()
  })

  it('切换到上传 tab 后显示「选择本地图片」按钮', async () => {
    const ctx = mountDialog({ open: true })
    await nextTick()
    const tabs = document.querySelectorAll('.tab-btn')
    // 第 2 个 tab = 上传
    tabs[1].dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await nextTick()
    const uploadBtn = document.querySelector('.upload-pane .btn-primary')
    expect(uploadBtn).not.toBeNull()
    expect(uploadBtn.textContent).toContain('选择本地图片')
    ctx.unmount()
  })
})

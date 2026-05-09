import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { ref, createApp, h, nextTick } from 'vue'
import MessageComposer from './MessageComposer.vue'

// D1-W22-04 / W22-06 / W22-07 MessageComposer 多附件 + 粘贴 + 拖拽 单测
//
// 用最小宿主 createApp 装载组件；通过 ref 暴露的 addFiles 直接驱动业务函数。
// dom 事件（paste / drop）走真实 DOM 触发。

function createMockFile(name, type = 'application/octet-stream', size = 100) {
  // 用 File 构造（jsdom 支持）；属性都齐
  return new File(['x'.repeat(size)], name, { type })
}

function mountComposer(initialProps = {}) {
  const composerRef = ref(null)
  const submitArg = ref(null)
  const overflowArg = ref(null)
  const root = document.createElement('div')
  document.body.appendChild(root)
  const app = createApp({
    setup() {
      return () =>
        h(MessageComposer, {
          ref: composerRef,
          busy: initialProps.busy || false,
          showCameraBtn: initialProps.showCameraBtn ?? null,
          onSubmit: (p) => (submitArg.value = p),
          onOverflow: (p) => (overflowArg.value = p)
        })
    }
  })
  app.mount(root)
  return {
    root,
    composerRef,
    submitArg,
    overflowArg,
    unmount: () => {
      app.unmount()
      root.remove()
    }
  }
}

describe('MessageComposer', () => {
  // jsdom 没有 createObjectURL；mock 一下
  let createObjectURLSpy
  let revokeObjectURLSpy

  beforeEach(() => {
    let counter = 0
    createObjectURLSpy = vi.fn(() => `blob:mock-${++counter}`)
    revokeObjectURLSpy = vi.fn()
    Object.defineProperty(URL, 'createObjectURL', { value: createObjectURLSpy, configurable: true })
    Object.defineProperty(URL, 'revokeObjectURL', { value: revokeObjectURLSpy, configurable: true })
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('addFiles 追加附件并保持顺序，超过 9 个触发 overflow', async () => {
    const ctx = mountComposer()
    await nextTick()
    const composer = ctx.composerRef.value
    const files = Array.from({ length: 10 }, (_, i) => createMockFile(`f${i}.txt`, 'text/plain'))
    const taken = composer.addFiles(files)
    expect(taken).toBe(9)
    expect(ctx.overflowArg.value).toEqual({ max: 9 })
    ctx.unmount()
  })

  it('addFiles 在 busy=true 时拒绝追加', async () => {
    const ctx = mountComposer({ busy: true })
    await nextTick()
    const composer = ctx.composerRef.value
    const taken = composer.addFiles([createMockFile('a.txt', 'text/plain')])
    expect(taken).toBe(0)
    ctx.unmount()
  })

  it('reset 释放所有 ObjectURL（image 附件）', async () => {
    const ctx = mountComposer()
    await nextTick()
    const composer = ctx.composerRef.value
    composer.addFiles([
      createMockFile('a.png', 'image/png'),
      createMockFile('b.jpg', 'image/jpeg')
    ])
    await nextTick()
    // template 渲染会触发 makePreviewUrl → createObjectURL
    expect(createObjectURLSpy).toHaveBeenCalledTimes(2)
    composer.reset()
    expect(revokeObjectURLSpy).toHaveBeenCalledTimes(2)
    ctx.unmount()
  })

  it('paste 事件捕获 image/* 项，非图片项忽略', async () => {
    const ctx = mountComposer()
    await nextTick()
    const composer = ctx.composerRef.value
    const textarea = ctx.root.querySelector('.composer-input')
    expect(textarea).not.toBeNull()
    // 模拟 ClipboardEvent + items
    const pngFile = createMockFile('paste.png', 'image/png')
    const txtFile = createMockFile('paste.txt', 'text/plain')
    const evt = new Event('paste', { bubbles: true, cancelable: true })
    Object.defineProperty(evt, 'clipboardData', {
      value: {
        items: [
          { kind: 'file', getAsFile: () => pngFile },
          { kind: 'file', getAsFile: () => txtFile },
          { kind: 'string', getAsFile: () => null }
        ]
      }
    })
    textarea.dispatchEvent(evt)
    await nextTick()
    // 只有 png 进入 pendingFiles：通过 attachment-grid 子节点数量验证
    const cards = ctx.root.querySelectorAll('.attachment-card')
    expect(cards.length).toBe(1)
    void composer
    ctx.unmount()
  })

  it('drop 事件接收 dataTransfer.files 并追加', async () => {
    const ctx = mountComposer()
    await nextTick()
    const root = ctx.root.querySelector('.composer')
    expect(root).not.toBeNull()
    const file = createMockFile('drop.pdf', 'application/pdf')
    const evt = new Event('drop', { bubbles: true, cancelable: true })
    Object.defineProperty(evt, 'dataTransfer', { value: { files: [file], types: ['Files'] } })
    root.dispatchEvent(evt)
    await nextTick()
    const cards = ctx.root.querySelectorAll('.attachment-card')
    expect(cards.length).toBe(1)
    ctx.unmount()
  })

  it('doSubmit 通过 emit 提供 text + files 数组与 file 单值兼容', async () => {
    const ctx = mountComposer()
    await nextTick()
    const composer = ctx.composerRef.value
    composer.addFiles([
      createMockFile('a.txt', 'text/plain'),
      createMockFile('b.txt', 'text/plain')
    ])
    await nextTick()
    // 找到 send 按钮并点击
    const sendBtn = ctx.root.querySelector('.send-btn')
    sendBtn.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await nextTick()
    expect(ctx.submitArg.value).toBeTruthy()
    expect(ctx.submitArg.value.files.length).toBe(2)
    expect(ctx.submitArg.value.file).toBe(ctx.submitArg.value.files[0])
    ctx.unmount()
  })
})

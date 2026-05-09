import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { createApp, h } from 'vue'
import { useVoiceRecorder } from './useVoiceRecorder'

// D1-W27-02 useVoiceRecorder 状态机测试
//
// 用 mock 的 MediaRecorder + getUserMedia 验证：
//   - start() / stop() / cancel() 状态切换
//   - dataavailable 收集 chunks
//   - 没有 MediaRecorder API → 'error' 状态
//   - 权限拒绝 → 'error' 状态 + error 文案
//
// 走 createApp 宿主以让 onBeforeUnmount 能正确注册

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
  return {
    result: captured,
    unmount: () => app.unmount()
  }
}

class FakeMediaRecorder {
  static isTypeSupported(t) {
    return t === 'audio/webm;codecs=opus' || t === 'audio/webm'
  }
  constructor(stream, opts) {
    this.stream = stream
    this.options = opts
    this._listeners = { dataavailable: [], stop: [], error: [] }
  }
  addEventListener(type, fn) {
    if (this._listeners[type]) this._listeners[type].push(fn)
  }
  removeEventListener(type, fn) {
    if (this._listeners[type]) {
      this._listeners[type] = this._listeners[type].filter((f) => f !== fn)
    }
  }
  start() { /* noop */ }
  stop() {
    // 立即同步触发 dataavailable + stop
    const chunk = new Blob(['x'.repeat(1024)], { type: 'audio/webm' })
    this._listeners.dataavailable.forEach((fn) => fn({ data: chunk }))
    this._listeners.stop.forEach((fn) => fn())
  }
}

function installFakeMediaRecorder() {
  Object.defineProperty(window, 'MediaRecorder', {
    configurable: true,
    writable: true,
    value: FakeMediaRecorder
  })
}

function uninstallFakeMediaRecorder() {
  delete window.MediaRecorder
}

function installFakeGetUserMedia(reject = false, errName = 'NotAllowedError') {
  const fakeTrack = { stop: vi.fn() }
  const fakeStream = { getTracks: () => [fakeTrack] }
  Object.defineProperty(navigator, 'mediaDevices', {
    configurable: true,
    writable: true,
    value: {
      getUserMedia: vi.fn(() => {
        if (reject) {
          const err = new Error(errName === 'NotAllowedError' ? 'Permission denied' : 'fail')
          err.name = errName
          return Promise.reject(err)
        }
        return Promise.resolve(fakeStream)
      })
    }
  })
  return { fakeStream, fakeTrack }
}

describe('useVoiceRecorder', () => {
  beforeEach(() => {
    installFakeMediaRecorder()
  })

  afterEach(() => {
    uninstallFakeMediaRecorder()
  })

  it('完整流程：idle → recording → stop() 返回 blob 与 durationSec', async () => {
    const { fakeTrack } = installFakeGetUserMedia(false)
    const { result, unmount } = runInComponent(() => useVoiceRecorder())
    expect(result.state.value).toBe('idle')

    await result.start()
    expect(result.state.value).toBe('recording')
    expect(result.isRecording.value).toBe(true)

    const out = await result.stop()
    expect(out).not.toBeNull()
    expect(out.blob).toBeInstanceOf(Blob)
    expect(out.blob.size).toBeGreaterThan(0)
    expect(out.durationSec).toBeGreaterThanOrEqual(1)
    expect(typeof out.mimeType).toBe('string')
    expect(out.mimeType.startsWith('audio/')).toBe(true)
    expect(result.state.value).toBe('idle')
    // stream tracks 已 stop
    expect(fakeTrack.stop).toHaveBeenCalled()

    unmount()
  })

  it('cancel() → 不返回 blob 且回到 idle', async () => {
    installFakeGetUserMedia(false)
    const { result, unmount } = runInComponent(() => useVoiceRecorder())
    await result.start()
    await result.cancel()
    expect(result.state.value).toBe('idle')
    expect(result.durationMs.value).toBe(0)
    unmount()
  })

  it('权限拒绝（NotAllowedError）→ error 状态 + 友好文案', async () => {
    installFakeGetUserMedia(true, 'NotAllowedError')
    const { result, unmount } = runInComponent(() => useVoiceRecorder())
    await expect(result.start()).rejects.toBeInstanceOf(Error)
    expect(result.state.value).toBe('error')
    expect(result.error.value).toBe('麦克风权限被拒绝')
    unmount()
  })

  it('其他权限错误 → error 状态 + 通用文案', async () => {
    installFakeGetUserMedia(true, 'NotFoundError')
    const { result, unmount } = runInComponent(() => useVoiceRecorder())
    await expect(result.start()).rejects.toBeInstanceOf(Error)
    expect(result.state.value).toBe('error')
    expect(result.error.value).not.toBe('麦克风权限被拒绝')
    expect(result.error.value.length).toBeGreaterThan(0)
    unmount()
  })

  it('当 MediaRecorder 不存在 → 立即 error', async () => {
    uninstallFakeMediaRecorder()
    installFakeGetUserMedia(false)
    const { result, unmount } = runInComponent(() => useVoiceRecorder())
    await expect(result.start()).rejects.toBeInstanceOf(Error)
    expect(result.state.value).toBe('error')
    expect(result.error.value).toContain('不支持')
    unmount()
  })

  it('stop() 在 idle 状态下安全返回 null', async () => {
    installFakeGetUserMedia(false)
    const { result, unmount } = runInComponent(() => useVoiceRecorder())
    const out = await result.stop()
    expect(out).toBeNull()
    expect(result.state.value).toBe('idle')
    unmount()
  })

  it('cancel() 在 idle 状态下安全无操作', async () => {
    installFakeGetUserMedia(false)
    const { result, unmount } = runInComponent(() => useVoiceRecorder())
    await result.cancel()
    expect(result.state.value).toBe('idle')
    unmount()
  })

  it('录音中再次 start() 抛错保护', async () => {
    installFakeGetUserMedia(false)
    const { result, unmount } = runInComponent(() => useVoiceRecorder())
    await result.start()
    await expect(result.start()).rejects.toBeInstanceOf(Error)
    expect(result.state.value).toBe('recording')
    await result.cancel()
    unmount()
  })
})

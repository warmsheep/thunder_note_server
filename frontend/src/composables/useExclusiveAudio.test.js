import { describe, it, expect, beforeEach, vi } from 'vitest'
import {
  notifyAudioPlay,
  notifyAudioPauseOrEnded,
  getCurrentAudio,
  __resetExclusiveAudioForTests
} from './useExclusiveAudio'

// D1-W27-03 全局语音播放互斥
//
// 验证：
//   - 第一个 audio play → 成为当前
//   - 第二个 audio play → 第一个被 pause()，第二个成为当前
//   - 当前 audio 触发 pause / ended → 全局 currentAudio 清空
//   - 不影响 audio 元素的 src / 其他属性

function makeFakeAudio() {
  return {
    pause: vi.fn(),
    play: vi.fn()
  }
}

describe('useExclusiveAudio', () => {
  beforeEach(() => {
    __resetExclusiveAudioForTests()
  })

  it('单个 audio play → 成为当前', () => {
    const a = makeFakeAudio()
    notifyAudioPlay(a)
    expect(getCurrentAudio()).toBe(a)
    expect(a.pause).not.toHaveBeenCalled()
  })

  it('第二个 audio play → 第一个被 pause()，第二个成为当前', () => {
    const a = makeFakeAudio()
    const b = makeFakeAudio()
    notifyAudioPlay(a)
    notifyAudioPlay(b)
    expect(a.pause).toHaveBeenCalledTimes(1)
    expect(getCurrentAudio()).toBe(b)
  })

  it('当前 audio 重新 play 不会自我 pause', () => {
    const a = makeFakeAudio()
    notifyAudioPlay(a)
    notifyAudioPlay(a)
    expect(a.pause).not.toHaveBeenCalled()
  })

  it('当前 audio pause / ended → 全局 currentAudio 清空', () => {
    const a = makeFakeAudio()
    notifyAudioPlay(a)
    notifyAudioPauseOrEnded(a)
    expect(getCurrentAudio()).toBeNull()
  })

  it('非当前 audio 触发 pauseOrEnded 不影响当前', () => {
    const a = makeFakeAudio()
    const b = makeFakeAudio()
    notifyAudioPlay(a)
    notifyAudioPauseOrEnded(b)
    expect(getCurrentAudio()).toBe(a)
  })

  it('null / undefined 输入安全', () => {
    notifyAudioPlay(null)
    notifyAudioPlay(undefined)
    expect(getCurrentAudio()).toBeNull()
    notifyAudioPauseOrEnded(null)
    notifyAudioPauseOrEnded(undefined)
    expect(getCurrentAudio()).toBeNull()
  })

  it('上一个被 pause 时即使第二个出错也不会留下残留状态', () => {
    const a = makeFakeAudio()
    a.pause = vi.fn(() => { throw new Error('boom') })
    const b = makeFakeAudio()
    notifyAudioPlay(a)
    notifyAudioPlay(b) // 不应抛出
    expect(getCurrentAudio()).toBe(b)
  })
})

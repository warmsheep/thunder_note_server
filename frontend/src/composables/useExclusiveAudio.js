// D1-W27-03 全局语音播放互斥
//
// 同一时刻只允许一个语音/音频元素处于 playing 状态：
//   - register(audioEl)：在该 audio 的 onPlay 时调用；自动暂停上一个
//   - unregister(audioEl)：组件卸载 / 该 audio 被替换时调用
//
// 设计：
//   - 用一个 module-level 弱引用（这里直接用普通引用，因为 audio 元素生命周期短）
//   - 不用 reactive，避免组件把它当依赖重渲染

let currentAudio = null

export function notifyAudioPlay(audioEl) {
  if (!audioEl) return
  if (currentAudio && currentAudio !== audioEl) {
    try { currentAudio.pause() } catch (_e) { /* ignore */ }
  }
  currentAudio = audioEl
}

export function notifyAudioPauseOrEnded(audioEl) {
  if (currentAudio === audioEl) {
    currentAudio = null
  }
}

export function getCurrentAudio() {
  return currentAudio
}

// 测试辅助：清空全局状态
export function __resetExclusiveAudioForTests() {
  currentAudio = null
}

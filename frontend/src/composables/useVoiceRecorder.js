import { ref, computed, onBeforeUnmount } from 'vue'

// D1-W27-02 浏览器录音状态机（与 Android `ChatRecordingHelper` 行为对齐）
//
// 状态：
//   - 'idle'        初始 / 已停止 / 已取消
//   - 'requesting'  正在请求 getUserMedia 权限
//   - 'recording'   MediaRecorder 正在收集音频块
//   - 'finalizing'  stop() 调用后等待 dataavailable 收尾
//   - 'error'       权限拒绝 / 浏览器不支持 / 录音异常
//
// 接口：
//   - start()                   开始录音；任何中间状态调用都直接 reject
//   - stop()  → Promise<{ blob, durationSec, mimeType }>  正常停止并返回 audio blob
//   - cancel() → Promise<void>  停止并丢弃数据；不发送
//   - dispose()                 释放 MediaStream + recorder（组件卸载必调）
//
// 设计：
//   - 录音前必须能成功 getUserMedia；任何路径下 stream 都要在 dispose / cancel / stop 后 stop tracks
//   - mimeType 探测顺序：webm;opus > webm > mp4 > ''（让浏览器自选）
//   - durationSec 通过 200ms tick 更新；停止时取最终精确值
//   - 多重保险：onstop 之外用 timeout 做兜底（部分浏览器 onstop 不触发）

const PROBE_MIME_TYPES = [
  'audio/webm;codecs=opus',
  'audio/webm',
  'audio/mp4;codecs=mp4a.40.2',
  'audio/mp4',
  ''
]

function pickSupportedMimeType() {
  if (typeof window === 'undefined' || !window.MediaRecorder) return null
  const isSupported = typeof window.MediaRecorder.isTypeSupported === 'function'
  for (const m of PROBE_MIME_TYPES) {
    if (!m) return ''
    if (isSupported && window.MediaRecorder.isTypeSupported(m)) return m
  }
  return null
}

export function useVoiceRecorder() {
  const state = ref('idle')
  const error = ref('')
  const durationMs = ref(0)
  const mimeTypeRef = ref('')

  let mediaStream = null
  let mediaRecorder = null
  let chunks = []
  let startedAt = 0
  let tickHandle = null
  let stopWaiters = null

  const isRecording = computed(() => state.value === 'recording')
  const durationSec = computed(() => Math.floor(durationMs.value / 1000))

  function clearTick() {
    if (tickHandle != null) {
      clearInterval(tickHandle)
      tickHandle = null
    }
  }

  function stopMediaStream() {
    if (mediaStream) {
      try {
        for (const track of mediaStream.getTracks()) {
          try { track.stop() } catch (_e) { /* ignore */ }
        }
      } catch (_e) { /* ignore */ }
      mediaStream = null
    }
  }

  function resetCore() {
    clearTick()
    stopMediaStream()
    mediaRecorder = null
    chunks = []
    startedAt = 0
    durationMs.value = 0
    mimeTypeRef.value = ''
  }

  async function start() {
    if (state.value !== 'idle' && state.value !== 'error') {
      throw new Error('Recorder is busy')
    }
    if (typeof window === 'undefined' || !window.MediaRecorder || !navigator?.mediaDevices?.getUserMedia) {
      state.value = 'error'
      error.value = '当前浏览器不支持录音'
      throw new Error(error.value)
    }
    const picked = pickSupportedMimeType()
    if (picked === null) {
      state.value = 'error'
      error.value = '当前浏览器没有可用的音频编码'
      throw new Error(error.value)
    }

    error.value = ''
    state.value = 'requesting'
    try {
      mediaStream = await navigator.mediaDevices.getUserMedia({ audio: true })
    } catch (e) {
      state.value = 'error'
      error.value = (e && e.name === 'NotAllowedError') ? '麦克风权限被拒绝' : (e?.message || '无法访问麦克风')
      throw new Error(error.value)
    }

    chunks = []
    mimeTypeRef.value = picked
    try {
      mediaRecorder = picked
        ? new window.MediaRecorder(mediaStream, { mimeType: picked })
        : new window.MediaRecorder(mediaStream)
    } catch (e) {
      stopMediaStream()
      state.value = 'error'
      error.value = e?.message || '录音器初始化失败'
      throw new Error(error.value)
    }

    mediaRecorder.addEventListener('dataavailable', (ev) => {
      if (ev && ev.data && ev.data.size > 0) {
        chunks.push(ev.data)
      }
    })
    mediaRecorder.addEventListener('error', (ev) => {
      // 录音过程中出错：标记 error 并尝试 stop（finalize）
      error.value = ev?.error?.message || '录音过程中出错'
      // 不直接 reject 当前 stop()；让 onstop 自然走到
    })

    startedAt = Date.now()
    durationMs.value = 0
    tickHandle = setInterval(() => {
      durationMs.value = Date.now() - startedAt
    }, 200)

    try {
      mediaRecorder.start()
    } catch (e) {
      stopMediaStream()
      state.value = 'error'
      error.value = e?.message || '录音启动失败'
      throw new Error(error.value)
    }
    state.value = 'recording'
  }

  function awaitStop() {
    return new Promise((resolve, reject) => {
      stopWaiters = { resolve, reject }
      const recorder = mediaRecorder
      if (!recorder) {
        stopWaiters = null
        resolve(null)
        return
      }
      const onStop = () => {
        recorder.removeEventListener('stop', onStop)
        const finalChunks = chunks
        const finalDur = Math.max(0, Date.now() - startedAt)
        const mt = mimeTypeRef.value || (finalChunks[0]?.type) || 'audio/webm'
        resolve({ chunks: finalChunks, durationMs: finalDur, mimeType: mt })
      }
      recorder.addEventListener('stop', onStop)

      // 兜底超时：部分浏览器 onstop 不触发
      setTimeout(() => {
        recorder.removeEventListener('stop', onStop)
        const finalChunks = chunks
        const finalDur = Math.max(0, Date.now() - startedAt)
        const mt = mimeTypeRef.value || (finalChunks[0]?.type) || 'audio/webm'
        if (stopWaiters) {
          stopWaiters = null
          resolve({ chunks: finalChunks, durationMs: finalDur, mimeType: mt })
        }
      }, 1500)

      try {
        recorder.stop()
      } catch (e) {
        recorder.removeEventListener('stop', onStop)
        stopWaiters = null
        reject(new Error(e?.message || '停止录音失败'))
      }
    })
  }

  async function stop() {
    if (state.value !== 'recording') {
      return null
    }
    state.value = 'finalizing'
    clearTick()
    try {
      const result = await awaitStop()
      if (!result) {
        resetCore()
        state.value = 'idle'
        return null
      }
      const { chunks: finalChunks, durationMs: finalDur, mimeType } = result
      stopMediaStream()
      mediaRecorder = null
      chunks = []
      startedAt = 0
      const blobType = mimeType || finalChunks[0]?.type || 'audio/webm'
      const blob = new Blob(finalChunks, { type: blobType })
      durationMs.value = finalDur
      state.value = 'idle'
      return {
        blob,
        durationMs: finalDur,
        durationSec: Math.max(1, Math.round(finalDur / 1000)),
        mimeType: blobType
      }
    } catch (e) {
      resetCore()
      state.value = 'error'
      error.value = e?.message || '停止录音失败'
      throw e
    }
  }

  async function cancel() {
    if (state.value !== 'recording' && state.value !== 'finalizing') {
      // 已经空闲 / 错误：仅清理资源
      resetCore()
      state.value = 'idle'
      return
    }
    state.value = 'finalizing'
    clearTick()
    try {
      // 优先调 stop 让 recorder 进入 inactive，再丢弃 chunks
      await awaitStop()
    } catch (_e) {
      // 静默：取消路径不向上抛
    }
    resetCore()
    state.value = 'idle'
  }

  function dispose() {
    clearTick()
    if (mediaRecorder) {
      try { mediaRecorder.stop() } catch (_e) { /* ignore */ }
    }
    resetCore()
    state.value = 'idle'
    error.value = ''
  }

  // 默认在组件卸载时清理
  onBeforeUnmount(() => {
    dispose()
  })

  return {
    state,
    error,
    durationMs,
    durationSec,
    isRecording,
    start,
    stop,
    cancel,
    dispose
  }
}

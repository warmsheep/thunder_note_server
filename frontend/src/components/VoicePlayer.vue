<script setup>
import { ref, computed, onBeforeUnmount, watch } from 'vue'
import { notifyAudioPlay, notifyAudioPauseOrEnded } from '../composables/useExclusiveAudio'

// D1-W28-03 自绘语音播放器（替代原生 <audio controls>，解决 audio controls 在
// Safari / 部分 Chrome 主题下出现的「大黑边」灰底问题）。
//
// 设计：
//   - 隐藏的 <audio> 元素负责真实解码 / 播放
//   - 自绘 ▶/⏸ 按钮 + 进度条 + 当前时间 / 总时长
//   - 进度条可点 / 可拖动 seek
//   - 与 useExclusiveAudio 协作：开始播放时上一个语音 / 音频自动暂停
//
// props:
//   - src: blob URL（由 MediaPreview 传入，鉴权 fetchAsObjectUrl 已完成）
//   - duration: 后端 mediaDuration（秒），用于在元数据加载完成前先撑出宽度
//   - widthPx: 容器宽度，由 MediaPreview 按 mediaDuration 线性映射后传入

const props = defineProps({
  src: { type: String, required: true },
  duration: { type: Number, default: 0 },
  widthPx: { type: Number, default: 160 }
})

const audioEl = ref(null)
const isPlaying = ref(false)
const currentTime = ref(0)
const totalDuration = ref(props.duration > 0 ? props.duration : 0)
const seeking = ref(false)

const displayDuration = computed(() => {
  // 优先用 audio 元数据里的 duration；元数据未到时回退到 props.duration
  if (totalDuration.value > 0 && Number.isFinite(totalDuration.value)) {
    return totalDuration.value
  }
  return props.duration > 0 ? props.duration : 0
})

const progressPercent = computed(() => {
  const d = displayDuration.value
  if (d <= 0) return 0
  return Math.min(100, Math.max(0, (currentTime.value / d) * 100))
})

function fmt(sec) {
  const s = Math.max(0, Math.floor(Number(sec) || 0))
  const mm = String(Math.floor(s / 60)).padStart(1, '0')
  const ss = String(s % 60).padStart(2, '0')
  return `${mm}:${ss}`
}

function onLoadedMetadata() {
  const d = audioEl.value?.duration
  if (Number.isFinite(d) && d > 0) {
    totalDuration.value = d
  }
}

function onTimeUpdate() {
  if (!seeking.value && audioEl.value) {
    currentTime.value = audioEl.value.currentTime || 0
  }
}

function onPlay() {
  isPlaying.value = true
  notifyAudioPlay(audioEl.value)
}

function onPauseOrEnded() {
  isPlaying.value = false
  notifyAudioPauseOrEnded(audioEl.value)
  if (audioEl.value && audioEl.value.ended) {
    currentTime.value = 0
  }
}

async function toggle() {
  const el = audioEl.value
  if (!el) return
  try {
    if (el.paused) {
      await el.play()
    } else {
      el.pause()
    }
  } catch (_e) {
    // 浏览器拒绝自动播放等错误：交还给 audio 原生 error 事件
  }
}

// 进度条 seek：按下 / 拖动 / 抬起
const trackEl = ref(null)
function seekFromClientX(clientX) {
  const el = trackEl.value
  const audio = audioEl.value
  if (!el || !audio) return
  const rect = el.getBoundingClientRect()
  const x = Math.min(Math.max(clientX - rect.left, 0), rect.width)
  const ratio = rect.width > 0 ? x / rect.width : 0
  const d = displayDuration.value
  if (d > 0) {
    const next = ratio * d
    audio.currentTime = next
    currentTime.value = next
  }
}
function onTrackPointerDown(e) {
  seeking.value = true
  seekFromClientX(e.clientX)
  if (e.target && e.target.setPointerCapture && e.pointerId != null) {
    try { e.target.setPointerCapture(e.pointerId) } catch (_e) { /* ignore */ }
  }
}
function onTrackPointerMove(e) {
  if (!seeking.value) return
  seekFromClientX(e.clientX)
}
function onTrackPointerUp() {
  seeking.value = false
}

// src 变化时重置状态（譬如同一个组件被复用渲染另一条消息）
watch(
  () => props.src,
  () => {
    isPlaying.value = false
    currentTime.value = 0
    totalDuration.value = props.duration > 0 ? props.duration : 0
  }
)

onBeforeUnmount(() => {
  // 互斥：自身正在播放时主动注销，避免 currentAudio 指向已销毁元素
  if (audioEl.value) {
    notifyAudioPauseOrEnded(audioEl.value)
  }
})
</script>

<template>
  <div class="voice-player" :style="{ width: widthPx + 'px' }">
    <button
      type="button"
      class="vp-play-btn"
      :aria-label="isPlaying ? '暂停' : '播放'"
      :title="isPlaying ? '暂停' : '播放'"
      @click="toggle"
    >{{ isPlaying ? '⏸' : '▶' }}</button>
    <div
      ref="trackEl"
      class="vp-track"
      role="slider"
      :aria-valuemin="0"
      :aria-valuemax="Math.floor(displayDuration)"
      :aria-valuenow="Math.floor(currentTime)"
      tabindex="0"
      @pointerdown="onTrackPointerDown"
      @pointermove="onTrackPointerMove"
      @pointerup="onTrackPointerUp"
      @pointercancel="onTrackPointerUp"
    >
      <div class="vp-track-fill" :style="{ width: progressPercent + '%' }"></div>
    </div>
    <span class="vp-time mono">{{ fmt(isPlaying || currentTime > 0 ? currentTime : displayDuration) }}</span>
    <!-- 真实音频元素，隐藏；由自绘控件驱动 -->
    <audio
      ref="audioEl"
      :src="src"
      preload="metadata"
      style="display:none"
      @loadedmetadata="onLoadedMetadata"
      @timeupdate="onTimeUpdate"
      @play="onPlay"
      @pause="onPauseOrEnded"
      @ended="onPauseOrEnded"
    ></audio>
  </div>
</template>

<style scoped>
/* D1-W28-08 微信 / 安卓风格语音条：
   - 容器透明，直接继承父气泡颜色（mine 绿 / other 浅灰），不再叠加 background/border 黑框
   - 按钮 / 进度条 / 时长全部用 currentColor + 半透明，无论气泡是哪种底色都清晰
   - 总体形态：[▶按钮 圆形] [横向进度条/波形] [时长]，与 Android `item_chat_message.xml`
     `voiceContainer` / `rightVoiceContainer` 结构一致 */
.voice-player {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 2px 0;
  background: transparent;
  border: none;
  border-radius: 0;
  max-width: 100%;
  box-sizing: border-box;
  color: inherit;
}
.vp-play-btn {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  border: none;
  background: rgba(0, 0, 0, 0.55);
  color: #ffffff;
  font-size: 12px;
  line-height: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  padding: 0;
  transition: background 0.15s, transform 0.05s;
}
.vp-play-btn:hover {
  background: rgba(0, 0, 0, 0.75);
}
.vp-play-btn:active {
  transform: scale(0.96);
}
.vp-track {
  flex: 1;
  height: 4px;
  border-radius: 2px;
  background: rgba(0, 0, 0, 0.18);
  position: relative;
  cursor: pointer;
  touch-action: none;
}
.vp-track:focus-visible {
  outline: 2px solid currentColor;
  outline-offset: 2px;
}
.vp-track-fill {
  position: absolute;
  inset: 0;
  width: 0;
  height: 100%;
  background: rgba(0, 0, 0, 0.55);
  border-radius: 2px;
  pointer-events: none;
}
.vp-time {
  flex-shrink: 0;
  font-size: 11px;
  color: inherit;
  opacity: 0.7;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
  min-width: 32px;
  text-align: right;
}
.mono {
  font-family: 'SFMono-Regular', Menlo, Consolas, monospace;
}
</style>

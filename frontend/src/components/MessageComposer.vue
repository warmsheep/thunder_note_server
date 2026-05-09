<script setup>
import { ref, computed, onBeforeUnmount, watch } from 'vue'
import { formatFileSize, inferMediaType } from '../utils/fileHelpers'
import { useVoiceRecorder } from '../composables/useVoiceRecorder'

// D1-W6 / D1-W22 消息输入区
//
// W22-04 多附件：pendingFile (单) → pendingFiles (多，max 9)
// W22-05 拍照入口：移动端 <input capture="environment"> 调起系统相机
// W22-06 粘贴图片：textarea @paste 裁出 clipboardData.items 里的 image/* 追加
// W22-07 拖拽：composer 区 @dragover/@drop 接收文件
//
// emit('submit', { text: string, files: File[] })
//   - 发送软重置交给父级决定（成功 reset / 失败保留）
//
// 注：向后兼容：父级仍可以读 payload.file（取 files[0]）并逐个上传。
//
// D1-W27-02 录音入口：
//   - composer 工具栏新增 🎤 按钮，点击进入录音状态，整行 composer 替换为录音 overlay：
//     ⏺ 录音中 0:05 [取消] [发送]
//   - 录音中其他按钮全禁用，避免上下文混乱
//   - 发送时 emit('submit-voice', { blob, fileName, durationSec, mimeType })，由父级
//     走 uploadFile + sendMessage(mediaType='voice', mediaDuration) 链路
//   - 失败 / 拒绝权限：显示 errorText，3s 后自动消失

const MAX_ATTACHMENTS = 9

const props = defineProps({
  busy: { type: Boolean, default: false },
  uploadProgress: { type: Number, default: 0 },
  // D1-W26-04 与 Android `chat_input_hint` 对齐为简洁版「输入内容...」；
  // 桌面端通过 textarea title 仍保留 Enter / Shift+Enter 教学提示
  placeholder: { type: String, default: '输入内容...' },
  // null=自动检测；显式 true/false 由父级覆盖
  showCameraBtn: { type: Boolean, default: null }
})

// W22-05 拍照按钮：默认仅移动端（pointer: coarse）显示
const autoShowCamera = computed(() => {
  if (typeof window === 'undefined' || !window.matchMedia) return false
  return window.matchMedia('(pointer: coarse)').matches
})
const cameraBtnVisible = computed(() =>
  props.showCameraBtn === null ? autoShowCamera.value : !!props.showCameraBtn
)

const emit = defineEmits(['submit', 'submit-voice', 'overflow'])

// D1-W27-02 浏览器录音状态机
const recorder = useVoiceRecorder()
const recorderError = ref('')
let recorderErrorTimer = null
function clearRecorderError() {
  recorderError.value = ''
  if (recorderErrorTimer) {
    clearTimeout(recorderErrorTimer)
    recorderErrorTimer = null
  }
}
function showRecorderError(msg) {
  recorderError.value = String(msg || '')
  if (recorderErrorTimer) clearTimeout(recorderErrorTimer)
  recorderErrorTimer = setTimeout(() => {
    recorderError.value = ''
    recorderErrorTimer = null
  }, 3000)
}
const isRecording = computed(() => recorder.state.value === 'recording')
const isFinalizingVoice = computed(() => recorder.state.value === 'finalizing')
const recordOverlayActive = computed(
  () => recorder.state.value === 'requesting' || isRecording.value || isFinalizingVoice.value
)
const recordDurationLabel = computed(() => {
  const sec = Math.max(0, Math.floor(recorder.durationMs.value / 1000))
  const mm = String(Math.floor(sec / 60)).padStart(1, '0')
  const ss = String(sec % 60).padStart(2, '0')
  return `${mm}:${ss}`
})

async function startRecording() {
  if (props.busy || recordOverlayActive.value) return
  clearRecorderError()
  try {
    await recorder.start()
  } catch (e) {
    showRecorderError(e?.message || '录音失败')
  }
}

async function finishRecordingAndSend() {
  if (!isRecording.value) return
  let result
  try {
    result = await recorder.stop()
  } catch (e) {
    showRecorderError(e?.message || '停止录音失败')
    return
  }
  if (!result || !result.blob || result.blob.size === 0) {
    showRecorderError('录音内容为空')
    return
  }
  const ext = pickExtensionForMime(result.mimeType)
  const fileName = `voice-${Date.now()}.${ext}`
  emit('submit-voice', {
    blob: result.blob,
    fileName,
    durationSec: result.durationSec,
    mimeType: result.mimeType
  })
}

async function cancelRecording() {
  if (!isRecording.value && !isFinalizingVoice.value) return
  try {
    await recorder.cancel()
  } catch (_e) {
    /* 取消路径静默 */
  }
}

function pickExtensionForMime(mt) {
  const m = String(mt || '').toLowerCase()
  if (m.includes('audio/webm')) return 'webm'
  if (m.includes('audio/mp4')) return 'm4a'
  if (m.includes('audio/ogg')) return 'ogg'
  if (m.includes('audio/mpeg')) return 'mp3'
  if (m.includes('audio/wav') || m.includes('audio/x-wav')) return 'wav'
  return 'webm'
}

const text = ref('')
const textareaEl = ref(null)
const fileInputEl = ref(null)
const cameraInputEl = ref(null)
const pendingFiles = ref([])
const dragActive = ref(false)

// 附件预览 URL：对 image / video 帮子元素生成 createObjectURL，避免重复创建
const previewUrls = ref(new Map()) // key: File 引用 → url

const canSend = computed(
  () => !props.busy && (text.value.trim().length > 0 || pendingFiles.value.length > 0)
)
const progressPercent = computed(() =>
  Math.max(0, Math.min(100, Math.round((props.uploadProgress || 0) * 100)))
)

function makePreviewUrl(file) {
  if (!file) return ''
  const cached = previewUrls.value.get(file)
  if (cached) return cached
  const t = inferMediaType(file)
  if (t === 'image' || t === 'video') {
    try {
      const u = URL.createObjectURL(file)
      previewUrls.value.set(file, u)
      return u
    } catch (_e) {
      return ''
    }
  }
  return ''
}

function releasePreviewUrl(file) {
  const u = previewUrls.value.get(file)
  if (u) {
    try {
      URL.revokeObjectURL(u)
    } catch (_e) {
      /* ignore */
    }
    previewUrls.value.delete(file)
  }
}

function kindOf(file) {
  return inferMediaType(file)
}

function pickFile() {
  if (props.busy) return
  fileInputEl.value?.click()
}

function pickCamera() {
  if (props.busy) return
  cameraInputEl.value?.click()
}

// 统一的多文件追加入口（裁切、去重、溢出提示）
function addFiles(rawFiles) {
  if (!rawFiles || !rawFiles.length || props.busy) return 0
  const incoming = Array.from(rawFiles).filter(Boolean)
  if (incoming.length === 0) return 0
  const remaining = MAX_ATTACHMENTS - pendingFiles.value.length
  if (remaining <= 0) {
    emit('overflow', { max: MAX_ATTACHMENTS })
    return 0
  }
  const take = incoming.slice(0, remaining)
  pendingFiles.value = pendingFiles.value.concat(take)
  if (incoming.length > take.length) {
    emit('overflow', { max: MAX_ATTACHMENTS })
  }
  return take.length
}

function onFileChange(e) {
  const list = e.target.files
  if (list && list.length) {
    addFiles(list)
  }
  // 重置 input value，保证选择同文件也可再次触发 change
  if (e.target) e.target.value = ''
}

function removeAt(idx) {
  const file = pendingFiles.value[idx]
  if (file) releasePreviewUrl(file)
  pendingFiles.value = pendingFiles.value.filter((_, i) => i !== idx)
}

function clearAll() {
  for (const f of pendingFiles.value) releasePreviewUrl(f)
  pendingFiles.value = []
}

function handleEnter(e) {
  if (e.shiftKey) return
  e.preventDefault()
  doSubmit()
}

function doSubmit() {
  if (!canSend.value) return
  emit('submit', {
    text: text.value,
    files: pendingFiles.value.slice(),
    // 向后兼容：个别调用方仍读 file 字段作为单附件的快路
    file: pendingFiles.value[0] || null
  })
  // 不清空：父级在确认 send 成功后再调 reset()，失败时保留输入（W6-06 / W9-02）
}

function reset() {
  text.value = ''
  clearAll()
}

// W22-06 粘贴图片直发：裁 clipboardData.items 里的 image/*
function onPaste(e) {
  if (props.busy) return
  const items = e.clipboardData && e.clipboardData.items
  if (!items || items.length === 0) return
  const collected = []
  for (const it of items) {
    if (it && it.kind === 'file') {
      const f = it.getAsFile()
      if (f && /^image\//i.test(f.type || '')) {
        // 给粘贴出来的 image 补上可读文件名，避免后端上传报错
        if (!f.name) {
          try {
            const renamed = new File([f], `pasted-${Date.now()}.png`, { type: f.type || 'image/png' })
            collected.push(renamed)
            continue
          } catch (_e) {
            // 老浏览器不支持 File 构造函数，那就直接用原 Blob
          }
        }
        collected.push(f)
      }
    }
  }
  if (collected.length) {
    e.preventDefault()
    addFiles(collected)
  }
}

// W22-07 拖拽文件到 composer
function onDragOver(e) {
  if (props.busy) return
  // 必须 preventDefault 才能触发 drop
  if (e.dataTransfer && e.dataTransfer.types && e.dataTransfer.types.includes('Files')) {
    e.preventDefault()
    dragActive.value = true
  }
}
function onDragLeave(e) {
  // dragleave 会在子元素间频繁触发；只在离开 composer 范围时重置
  if (e.currentTarget && !e.currentTarget.contains(e.relatedTarget)) {
    dragActive.value = false
  }
}
function onDrop(e) {
  dragActive.value = false
  if (props.busy) return
  const files = e.dataTransfer && e.dataTransfer.files
  if (files && files.length) {
    e.preventDefault()
    addFiles(files)
  }
}

// 组件卸载时释放所有 ObjectURL + 清理录音错误 timer
// 录音器 dispose 已经由 useVoiceRecorder 内部 onBeforeUnmount 注册
onBeforeUnmount(() => {
  for (const u of previewUrls.value.values()) {
    try {
      URL.revokeObjectURL(u)
    } catch (_e) {
      /* ignore */
    }
  }
  previewUrls.value.clear()
  if (recorderErrorTimer) {
    clearTimeout(recorderErrorTimer)
    recorderErrorTimer = null
  }
})

// busy 变化时不动 pendingFiles，避免发送期间误删
watch(
  () => props.busy,
  () => {
    /* placeholder for future side effects */
  }
)

// D1-W24-05 草稿保留所需：父级（ChatView）通过 ref 读写文本草稿
function getText() {
  return text.value
}
function setText(value) {
  text.value = value == null ? '' : String(value)
}

defineExpose({
  reset,
  focus: () => textareaEl.value?.focus(),
  // 外部（如 ChatView、CardEditorDialog）可调用追加附件
  addFiles,
  // 草稿读写
  getText,
  setText
})
</script>

<template>
  <div
    class="composer"
    :class="{ 'drag-active': dragActive }"
    @dragover="onDragOver"
    @dragleave="onDragLeave"
    @drop="onDrop"
  >
    <!-- W22-04 多文件 input：multiple，涵盖 image/video/file 三种 -->
    <input
      ref="fileInputEl"
      type="file"
      class="file-hidden"
      multiple
      @change="onFileChange"
    />
    <!-- W22-05 拍照 input：accept="image/*" + capture="environment"（移动端调起后置摄像头） -->
    <input
      ref="cameraInputEl"
      type="file"
      class="file-hidden"
      accept="image/*"
      capture="environment"
      @change="onFileChange"
    />

    <!-- W22-04 多附件预览区 -->
    <div v-if="pendingFiles.length > 0" class="attachment-grid">
      <div
        v-for="(f, idx) in pendingFiles"
        :key="idx"
        class="attachment-card"
        :class="`kind-${kindOf(f)}`"
      >
        <img
          v-if="kindOf(f) === 'image'"
          :src="makePreviewUrl(f)"
          :alt="f.name"
          class="attachment-thumb"
        />
        <video
          v-else-if="kindOf(f) === 'video'"
          :src="makePreviewUrl(f)"
          class="attachment-thumb"
          muted
          preload="metadata"
        ></video>
        <span v-else class="attachment-icon" aria-hidden="true">
          {{ kindOf(f) === 'audio' ? '🎵' : '📄' }}
        </span>
        <div class="attachment-info">
          <span class="attachment-name" :title="f.name">{{ f.name }}</span>
          <span class="attachment-size">{{ formatFileSize(f.size) }}</span>
        </div>
        <button
          v-if="!busy"
          type="button"
          class="attachment-remove"
          :title="'移除'"
          :aria-label="'移除附件 ' + f.name"
          @click="removeAt(idx)"
        >×</button>
      </div>
    </div>

    <div
      v-if="busy && progressPercent > 0 && progressPercent < 100"
      class="progress-bar"
      role="progressbar"
      :aria-valuenow="progressPercent"
    >
      <div class="progress-fill" :style="{ width: progressPercent + '%' }"></div>
      <span class="progress-text">{{ progressPercent }}%</span>
    </div>

    <!-- D1-W27-02 录音 overlay：录音状态下覆盖整行 composer，
         显示 ⏺ 录音中 + 计时器 + 取消 / 发送按钮。
         为了避免与外层拖拽监听冲突，overlay 没有 pointer-events:none。 -->
    <div v-if="recordOverlayActive" class="record-overlay" role="status" aria-live="polite">
      <span class="record-dot" aria-hidden="true"></span>
      <span class="record-label">
        {{ recorder.state.value === 'requesting' ? '请求麦克风...' :
            isFinalizingVoice ? '处理中...' : '录音中' }}
      </span>
      <span class="record-time mono">{{ recordDurationLabel }}</span>
      <span class="record-spacer"></span>
      <button
        type="button"
        class="record-btn record-cancel"
        :disabled="!isRecording && !isFinalizingVoice"
        :title="'取消录音'"
        @click="cancelRecording"
      >取消</button>
      <button
        type="button"
        class="record-btn record-send"
        :disabled="!isRecording || isFinalizingVoice"
        :title="'发送录音'"
        @click="finishRecordingAndSend"
      >发送</button>
    </div>

    <p
      v-if="recorderError && !recordOverlayActive"
      class="record-error"
      role="alert"
    >{{ recorderError }}</p>

    <div v-if="!recordOverlayActive" class="composer-row">
      <button
        type="button"
        class="attach-btn"
        :disabled="busy"
        :title="'添加附件（可多选）'"
        @click="pickFile"
      >📎</button>
      <button
        v-if="cameraBtnVisible"
        type="button"
        class="attach-btn"
        :disabled="busy"
        :title="'拍照'"
        @click="pickCamera"
      >📷</button>
      <!-- D1-W27-02 麦克风：点击进入录音状态 -->
      <button
        type="button"
        class="attach-btn"
        :disabled="busy"
        :title="'录制语音'"
        :aria-label="'录制语音'"
        @click="startRecording"
      >🎤</button>

      <textarea
        ref="textareaEl"
        v-model="text"
        class="composer-input"
        :placeholder="placeholder"
        :title="'Enter 发送 · Shift + Enter 换行'"
        :disabled="busy"
        rows="2"
        @keydown.enter="handleEnter"
        @paste="onPaste"
      ></textarea>

      <button
        type="button"
        class="send-btn"
        :disabled="!canSend"
        @click="doSubmit"
      >{{ busy ? '发送中...' : '发送' }}</button>
    </div>

    <!-- W22-07 拖拽浮层提示：仅在 dragActive 时覆盖 composer，pointer-events:none 避免拦截 drop -->
    <div v-if="dragActive" class="drag-overlay" aria-hidden="true">
      <span>松手上传 · 最多 {{ MAX_ATTACHMENTS }} 个附件</span>
    </div>
  </div>
</template>

<style scoped>
.composer {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  border-top: 1px solid var(--color-divider);
  background: var(--color-surface);
}
.composer.drag-active {
  outline: 2px dashed var(--color-primary);
  outline-offset: -4px;
}
.file-hidden {
  display: none;
}

/* W22-04 多附件网格 */
.attachment-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 8px;
  padding: 6px 0;
}
.attachment-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 6px;
  background: var(--color-bg);
  border: 1px solid var(--color-divider);
  border-radius: var(--radius-md);
  font-size: 12px;
  color: var(--color-text-secondary);
}
.attachment-thumb {
  width: 100%;
  height: 90px;
  object-fit: cover;
  background: #000;
  border-radius: var(--radius-sm);
}
.attachment-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 90px;
  background: var(--color-surface);
  border: 1px solid var(--color-divider);
  border-radius: var(--radius-sm);
  font-size: 28px;
}
.attachment-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.attachment-name {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.attachment-size {
  font-size: 11px;
  color: var(--color-text-hint);
}
.attachment-remove {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  border: none;
  background: rgba(0, 0, 0, 0.55);
  color: #ffffff;
  cursor: pointer;
  font-size: 14px;
  line-height: 1;
}
.attachment-remove:hover {
  background: var(--color-danger);
}

.progress-bar {
  position: relative;
  height: 18px;
  background: var(--color-bg);
  border-radius: var(--radius-sm);
  overflow: hidden;
  border: 1px solid var(--color-divider);
}
.progress-fill {
  height: 100%;
  background: var(--color-primary);
  transition: width 0.15s ease-out;
}
.progress-text {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: var(--color-text-primary);
}

.composer-row {
  display: flex;
  align-items: flex-end;
  gap: 8px;
}
.attach-btn {
  width: 36px;
  height: 36px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  cursor: pointer;
  font-size: 18px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.attach-btn:hover:not(:disabled) {
  border-color: var(--color-primary);
}
.attach-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.composer-input {
  flex: 1;
  resize: none;
  padding: 8px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  font-family: inherit;
  font-size: 14px;
  line-height: 1.5;
  outline: none;
  background: var(--color-surface);
  color: var(--color-text-primary);
  min-height: 48px;
  max-height: 160px;
}
.composer-input:focus {
  border-color: var(--color-primary);
}
.composer-input:disabled {
  background: var(--color-bg);
  cursor: not-allowed;
}
.send-btn {
  padding: 8px 18px;
  border: none;
  border-radius: var(--radius-md);
  background: var(--color-primary);
  color: #ffffff;
  font-size: 14px;
  cursor: pointer;
}
.send-btn:hover:not(:disabled) {
  background: var(--color-primary-dark);
}
.send-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

/* D1-W27-02 录音 overlay：占用 composer-row 位置，与 Android 录音条视觉密度对齐 */
.record-overlay {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid var(--color-divider);
  background: var(--color-bg);
  border-radius: var(--radius-md);
}
.record-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--color-danger);
  animation: tn-record-pulse 1s ease-in-out infinite;
}
@keyframes tn-record-pulse {
  0%, 100% { opacity: 0.35; transform: scale(0.85); }
  50%      { opacity: 1;    transform: scale(1.1); }
}
.record-label {
  font-size: 13px;
  color: var(--color-text-primary);
}
.record-time {
  font-size: 13px;
  color: var(--color-text-secondary);
  font-variant-numeric: tabular-nums;
}
.record-spacer {
  flex: 1;
}
.record-btn {
  padding: 6px 14px;
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  color: var(--color-text-primary);
  font-size: 13px;
  cursor: pointer;
}
.record-btn:hover:not(:disabled) {
  border-color: var(--color-primary);
  color: var(--color-primary);
}
.record-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.record-cancel:hover:not(:disabled) {
  border-color: var(--color-danger);
  color: var(--color-danger);
}
.record-send {
  background: var(--color-primary);
  color: #ffffff;
  border-color: var(--color-primary);
}
.record-send:hover:not(:disabled) {
  background: var(--color-primary-dark);
  border-color: var(--color-primary-dark);
  color: #ffffff;
}
.record-error {
  margin: 0;
  padding: 6px 10px;
  font-size: 12px;
  color: var(--color-danger);
  background: var(--color-danger-bg, rgba(220, 38, 38, 0.08));
  border-radius: var(--radius-sm);
}
.mono {
  font-family: 'SFMono-Regular', Menlo, Consolas, monospace;
}

/* W22-07 拖拽浮层 */
.drag-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(var(--color-primary-rgb, 37, 99, 235), 0.08);
  color: var(--color-primary);
  font-size: 14px;
  font-weight: 500;
  pointer-events: none;
  border-radius: var(--radius-md);
}
</style>

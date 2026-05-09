<script setup>
import { ref, computed, watch, onBeforeUnmount, nextTick } from 'vue'
import { uploadFile } from '../api/files'
import { createCompositeMessage } from '../api/messages'
import { formatFileSize, inferMediaType } from '../utils/fileHelpers'
import { applyInlineToken, toggleLinePrefix } from '../utils/markdownEditor'

// D1-W22-03 多媒体卡片新建对话框
//
// 流程：
//   1. 用户填标题（必填，maxLength 50）+ 内容（可选）+ 选最多 9 个附件
//   2. 点「保存」 → 串行 uploadFile 拿 objectName → 用 createCompositeMessage 落卡片
//   3. 后端按附件类型自动判 cardType；客户端不再做混合类型判断
//
// 与 MessageComposer 的差异：
//   - 这里 textarea 是「卡片正文」（最终落到 payload.summary 兜底 / item caption），不是发文本消息
//   - 提交一次 = 服务端落一条 mediaType=COMPOSITE 卡片消息，原始单文件消息不会进入会话

const MAX_ATTACHMENTS = 9
const MAX_TITLE_LEN = 50

const props = defineProps({
  open: { type: Boolean, default: false },
  // 目标会话二选一（由父级传入）
  flashNoteId: { type: [Number, String], default: null },
  peerUserId: { type: [Number, String], default: null }
})

const emit = defineEmits(['cancel', 'created', 'update:open'])

const titleEl = ref(null)
const contentEl = ref(null)
const title = ref('')
const content = ref('')
const pendingFiles = ref([])
const previewUrls = ref(new Map())
const dragActive = ref(false)
const submitting = ref(false)
const uploadProgress = ref(0)
const uploadingIndex = ref(-1)
const errorMessage = ref('')

const fileInputEl = ref(null)

const canSubmit = computed(
  () =>
    !submitting.value &&
    title.value.trim().length > 0 &&
    title.value.trim().length <= MAX_TITLE_LEN &&
    pendingFiles.value.length > 0 &&
    pendingFiles.value.length <= MAX_ATTACHMENTS &&
    (toLong(props.flashNoteId) != null || toLong(props.peerUserId) != null)
)

function toLong(v) {
  if (v == null) return null
  const n = Number(v)
  return Number.isFinite(n) ? n : null
}

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
  if (submitting.value) return
  fileInputEl.value?.click()
}

function addFiles(rawFiles) {
  if (!rawFiles || !rawFiles.length || submitting.value) return 0
  const incoming = Array.from(rawFiles).filter(Boolean)
  if (incoming.length === 0) return 0
  const remaining = MAX_ATTACHMENTS - pendingFiles.value.length
  if (remaining <= 0) {
    errorMessage.value = `一次最多 ${MAX_ATTACHMENTS} 个附件`
    return 0
  }
  const take = incoming.slice(0, remaining)
  pendingFiles.value = pendingFiles.value.concat(take)
  if (incoming.length > take.length) {
    errorMessage.value = `已添加前 ${take.length} 个，超出 ${MAX_ATTACHMENTS} 上限`
  }
  return take.length
}

function onFileChange(e) {
  if (e.target.files && e.target.files.length) addFiles(e.target.files)
  if (e.target) e.target.value = ''
}

function removeAt(idx) {
  const f = pendingFiles.value[idx]
  if (f) releasePreviewUrl(f)
  pendingFiles.value = pendingFiles.value.filter((_, i) => i !== idx)
}

function clearAll() {
  for (const f of pendingFiles.value) releasePreviewUrl(f)
  pendingFiles.value = []
}

function reset() {
  title.value = ''
  content.value = ''
  clearAll()
  errorMessage.value = ''
  uploadProgress.value = 0
  uploadingIndex.value = -1
}

// W22-07 拖拽
function onDragOver(e) {
  if (submitting.value) return
  if (e.dataTransfer && e.dataTransfer.types && e.dataTransfer.types.includes('Files')) {
    e.preventDefault()
    dragActive.value = true
  }
}
function onDragLeave(e) {
  if (e.currentTarget && !e.currentTarget.contains(e.relatedTarget)) {
    dragActive.value = false
  }
}
function onDrop(e) {
  dragActive.value = false
  if (submitting.value) return
  const files = e.dataTransfer && e.dataTransfer.files
  if (files && files.length) {
    e.preventDefault()
    addFiles(files)
  }
}

// W22-06 粘贴
function onPaste(e) {
  if (submitting.value) return
  const items = e.clipboardData && e.clipboardData.items
  if (!items || items.length === 0) return
  const collected = []
  for (const it of items) {
    if (it && it.kind === 'file') {
      const f = it.getAsFile()
      if (f && /^image\//i.test(f.type || '')) {
        if (!f.name) {
          try {
            const renamed = new File([f], `pasted-${Date.now()}.png`, { type: f.type || 'image/png' })
            collected.push(renamed)
            continue
          } catch (_e) { /* ignore */ }
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

async function submit() {
  if (!canSubmit.value) return
  errorMessage.value = ''
  submitting.value = true
  uploadingIndex.value = 0
  uploadProgress.value = 0

  // 串行上传 → 收集 items
  const items = []
  try {
    for (let i = 0; i < pendingFiles.value.length; i++) {
      uploadingIndex.value = i
      uploadProgress.value = 0
      const file = pendingFiles.value[i]
      const result = await uploadFile(file, {
        onUploadProgress: (e) => {
          if (e && e.total) {
            uploadProgress.value = e.loaded / e.total
          }
        }
      })
      const objectName = result?.objectName
      if (!objectName) {
        throw new Error('上传失败：缺少 objectName')
      }
      items.push({
        type: inferMediaType(file),
        mediaUrl: objectName,
        fileName: result?.originalFilename || file.name,
        fileSize: file.size != null ? Number(file.size) : null
      })
    }
    uploadingIndex.value = -1
    uploadProgress.value = 0

    // 一次性创建 COMPOSITE
    const created = await createCompositeMessage({
      title: title.value,
      content: content.value,
      flashNoteId: toLong(props.flashNoteId),
      receiverId: toLong(props.peerUserId),
      items
    })
    emit('created', created)
    reset()
    emit('update:open', false)
  } catch (e) {
    errorMessage.value = e?.serverMessage || e?.message || '保存失败'
  } finally {
    submitting.value = false
    uploadingIndex.value = -1
    uploadProgress.value = 0
  }
}

function cancel() {
  if (submitting.value) return
  emit('cancel')
  emit('update:open', false)
}

watch(
  () => props.open,
  async (next) => {
    if (next) {
      await nextTick()
      titleEl.value?.focus()
    }
  }
)

onBeforeUnmount(() => {
  for (const u of previewUrls.value.values()) {
    try {
      URL.revokeObjectURL(u)
    } catch (_e) { /* ignore */ }
  }
  previewUrls.value.clear()
})

// D1-W25-03 markdown 工具栏：粗体 / 斜体 / 引用 / 待办
//
// 与 Android `CardEditorFragment` 行为对齐：
//   - 粗体 / 斜体（行内）：选中文本两侧加 ** / *；未选中则插入 ****，光标在中间
//   - 引用 / 待办（行前缀）：当前行（或选中跨多行）加 / 取消 `> ` / `- [ ] `
//
// selection 同步：函数走纯 utils，组件这边只负责把结果回写 + restore selection
function applyEditorEdit(result) {
  const ta = contentEl.value
  if (!ta) return
  content.value = result.text
  // textarea 回写后下一帧再设 selection（DOM 还没拿到新 value 时 setSelectionRange 会失效）
  nextTick(() => {
    try {
      ta.focus()
      ta.setSelectionRange(result.selStart, result.selEnd)
    } catch (_e) { /* ignore */ }
  })
}

function applyToken(prefix, suffix) {
  const ta = contentEl.value
  if (!ta) return
  const result = applyInlineToken({
    text: content.value,
    selStart: ta.selectionStart || 0,
    selEnd: ta.selectionEnd || 0,
    prefix,
    suffix
  })
  applyEditorEdit(result)
}

function applyLinePrefix(prefix) {
  const ta = contentEl.value
  if (!ta) return
  const result = toggleLinePrefix({
    text: content.value,
    selStart: ta.selectionStart || 0,
    selEnd: ta.selectionEnd || 0,
    prefix
  })
  applyEditorEdit(result)
}

function onFmtBold() { applyToken('**', '**') }
function onFmtItalic() { applyToken('*', '*') }
function onFmtQuote() { applyLinePrefix('> ') }
function onFmtTodo() { applyLinePrefix('- [ ] ') }

defineExpose({ reset })
</script>

<template>
  <div v-if="open" class="modal-overlay" @click.self="cancel">
    <div
      class="modal"
      role="dialog"
      aria-label="新建多媒体卡片"
      :class="{ 'drag-active': dragActive }"
      @dragover="onDragOver"
      @dragleave="onDragLeave"
      @drop="onDrop"
    >
      <header class="modal-header">
        <h2 class="modal-title">新建多媒体卡片</h2>
        <button type="button" class="modal-close" :disabled="submitting" @click="cancel">×</button>
      </header>

      <div class="modal-body">
        <div class="form-row">
          <label class="form-label" for="cardEditorTitle">
            标题 <span class="required">*</span>
            <span class="counter">{{ title.length }}/{{ MAX_TITLE_LEN }}</span>
          </label>
          <input
            id="cardEditorTitle"
            ref="titleEl"
            v-model="title"
            type="text"
            class="form-input"
            :maxlength="MAX_TITLE_LEN"
            :disabled="submitting"
            placeholder="给这张卡片起个名字"
          />
        </div>

        <div class="form-row">
          <label class="form-label" for="cardEditorContent">正文（可选）</label>
          <!-- D1-W25-03 markdown 工具栏：与 Android `CardEditorFragment` 4 个按钮对齐 -->
          <div class="md-toolbar" role="toolbar" aria-label="markdown 工具栏">
            <button
              type="button"
              class="md-btn"
              :disabled="submitting"
              :title="'粗体（**...**）'"
              aria-label="粗体"
              @click="onFmtBold"
            ><b>B</b></button>
            <button
              type="button"
              class="md-btn"
              :disabled="submitting"
              :title="'斜体（*...*）'"
              aria-label="斜体"
              @click="onFmtItalic"
            ><i>I</i></button>
            <button
              type="button"
              class="md-btn"
              :disabled="submitting"
              :title="'引用（行首 > ）'"
              aria-label="引用"
              @click="onFmtQuote"
            >❝</button>
            <button
              type="button"
              class="md-btn"
              :disabled="submitting"
              :title="'待办（行首 - [ ] ）'"
              aria-label="待办"
              @click="onFmtTodo"
            >☐</button>
          </div>
          <textarea
            id="cardEditorContent"
            ref="contentEl"
            v-model="content"
            class="form-textarea"
            rows="3"
            :disabled="submitting"
            placeholder="写点说明...（可粘贴图片直接添加）"
            @paste="onPaste"
          ></textarea>
        </div>

        <div class="form-row">
          <div class="form-label-row">
            <span class="form-label">附件 <span class="counter">{{ pendingFiles.length }}/{{ MAX_ATTACHMENTS }}</span></span>
            <button
              type="button"
              class="btn-text"
              :disabled="submitting || pendingFiles.length === 0"
              @click="clearAll"
            >清空</button>
          </div>

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
              <span
                v-if="submitting && uploadingIndex === idx"
                class="attachment-progress"
                :title="'上传中'"
              >{{ Math.round(uploadProgress * 100) }}%</span>
              <button
                v-if="!submitting"
                type="button"
                class="attachment-remove"
                :title="'移除'"
                :aria-label="'移除附件 ' + f.name"
                @click="removeAt(idx)"
              >×</button>
            </div>
          </div>

          <button
            type="button"
            class="btn-add-file"
            :disabled="submitting || pendingFiles.length >= MAX_ATTACHMENTS"
            @click="pickFile"
          >+ 添加附件</button>
          <input
            ref="fileInputEl"
            type="file"
            class="file-hidden"
            multiple
            @change="onFileChange"
          />
          <p class="hint">
            支持图片 / 视频 / 文件；可粘贴图片或拖拽到对话框任意位置；最多 {{ MAX_ATTACHMENTS }} 个。
          </p>
        </div>

        <p v-if="errorMessage" class="error-text">{{ errorMessage }}</p>
      </div>

      <footer class="modal-footer">
        <button type="button" class="btn-secondary" :disabled="submitting" @click="cancel">取消</button>
        <button
          type="button"
          class="btn-primary"
          :disabled="!canSubmit"
          @click="submit"
        >{{ submitting ? '保存中...' : '保存为卡片' }}</button>
      </footer>

      <div v-if="dragActive" class="drag-overlay" aria-hidden="true">
        <span>松手即可添加为附件</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 16px;
}
.modal {
  position: relative;
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  width: 100%;
  max-width: 640px;
  max-height: calc(100vh - 32px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.25);
}
.modal.drag-active {
  outline: 2px dashed var(--color-primary);
  outline-offset: -4px;
}
.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  border-bottom: 1px solid var(--color-divider);
}
.modal-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}
.modal-close {
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  font-size: 22px;
  line-height: 1;
  cursor: pointer;
  color: var(--color-text-secondary);
}
.modal-body {
  padding: 14px 18px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 12px 18px;
  border-top: 1px solid var(--color-divider);
}
.form-row {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.form-label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.form-label {
  font-size: 13px;
  color: var(--color-text-secondary);
}
.required {
  color: var(--color-danger);
}
.counter {
  color: var(--color-text-hint);
  margin-left: 6px;
  font-size: 12px;
}
.form-input,
.form-textarea {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  font-family: inherit;
  font-size: 14px;
  color: var(--color-text-primary);
  background: var(--color-surface);
  outline: none;
  box-sizing: border-box;
}
.form-input:focus,
.form-textarea:focus {
  border-color: var(--color-primary);
}
/* D1-W25-03 markdown 工具栏：粗体 / 斜体 / 引用 / 待办 4 个按钮 */
.md-toolbar {
  display: flex;
  gap: 4px;
  margin-bottom: 4px;
}
.md-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  padding: 0;
  border: 1px solid var(--color-border);
  border-radius: 4px;
  background: var(--color-surface);
  color: var(--color-text-primary);
  font-size: 13px;
  line-height: 1;
  cursor: pointer;
}
.md-btn:hover:not(:disabled) {
  border-color: var(--color-primary);
  color: var(--color-primary);
}
.md-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.md-btn b { font-weight: 700; }
.md-btn i { font-style: italic; font-family: serif; }

.form-textarea {
  resize: vertical;
  min-height: 60px;
  line-height: 1.5;
}

.attachment-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 8px;
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
.attachment-progress {
  position: absolute;
  top: 4px;
  left: 4px;
  padding: 2px 6px;
  background: rgba(0, 0, 0, 0.6);
  color: #ffffff;
  border-radius: 10px;
  font-size: 11px;
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

.btn-add-file {
  align-self: flex-start;
  padding: 6px 14px;
  border: 1px dashed var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  font-size: 13px;
  color: var(--color-text-secondary);
  cursor: pointer;
}
.btn-add-file:hover:not(:disabled) {
  border-color: var(--color-primary);
  color: var(--color-primary);
}
.btn-add-file:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.btn-text {
  border: none;
  background: transparent;
  color: var(--color-primary);
  cursor: pointer;
  font-size: 12px;
}
.btn-text:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.file-hidden {
  display: none;
}
.hint {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-hint);
}
.error-text {
  margin: 0;
  padding: 8px 10px;
  border-radius: var(--radius-sm);
  background: rgba(239, 68, 68, 0.1);
  color: var(--color-danger);
  font-size: 13px;
}

.btn-secondary {
  padding: 7px 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  color: var(--color-text-primary);
  cursor: pointer;
  font-size: 14px;
}
.btn-secondary:hover:not(:disabled) {
  border-color: var(--color-primary);
  color: var(--color-primary);
}
.btn-primary {
  padding: 7px 16px;
  border: none;
  border-radius: var(--radius-md);
  background: var(--color-primary);
  color: #ffffff;
  cursor: pointer;
  font-size: 14px;
}
.btn-primary:hover:not(:disabled) {
  background: var(--color-primary-dark);
}
.btn-primary:disabled,
.btn-secondary:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.drag-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.08);
  color: var(--color-primary);
  font-size: 14px;
  font-weight: 500;
  pointer-events: none;
  border-radius: var(--radius-lg);
}
</style>

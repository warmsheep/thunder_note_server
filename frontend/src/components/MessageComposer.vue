<script setup>
import { ref, computed } from 'vue'
import { formatFileSize } from '../utils/fileHelpers'

const props = defineProps({
  busy: { type: Boolean, default: false },
  uploadProgress: { type: Number, default: 0 },
  placeholder: { type: String, default: '输入消息（Enter 发送，Shift+Enter 换行）' }
})

const emit = defineEmits(['submit'])

const text = ref('')
const textareaEl = ref(null)
const fileInputEl = ref(null)
const pendingFile = ref(null)

const canSend = computed(() => !props.busy && (text.value.trim().length > 0 || pendingFile.value != null))
const progressPercent = computed(() => Math.max(0, Math.min(100, Math.round((props.uploadProgress || 0) * 100))))

function pickFile() {
  if (props.busy) return
  fileInputEl.value?.click()
}

function onFileChange(e) {
  const f = e.target.files && e.target.files[0]
  pendingFile.value = f || null
  // 重置 input value，保证选择同一个文件也能再次触发 change
  if (fileInputEl.value) fileInputEl.value.value = ''
}

function clearFile() {
  pendingFile.value = null
}

function handleEnter(e) {
  if (e.shiftKey) return
  e.preventDefault()
  doSubmit()
}

function doSubmit() {
  if (!canSend.value) return
  emit('submit', { text: text.value, file: pendingFile.value })
  // 不清空：父级在确认 send 成功后再调 reset()，失败时保留输入（W6-06 / W9-02）
}

function reset() {
  text.value = ''
  pendingFile.value = null
}

defineExpose({ reset, focus: () => textareaEl.value?.focus() })
</script>

<template>
  <div class="composer">
    <input
      ref="fileInputEl"
      type="file"
      class="file-hidden"
      @change="onFileChange"
    />

    <div v-if="pendingFile" class="attachment-bar">
      <span class="attachment-name">📎 {{ pendingFile.name }}</span>
      <span class="attachment-size">{{ formatFileSize(pendingFile.size) }}</span>
      <button
        v-if="!busy"
        type="button"
        class="attachment-remove"
        :title="'移除附件'"
        @click="clearFile"
      >×</button>
    </div>

    <div v-if="busy && progressPercent > 0 && progressPercent < 100" class="progress-bar" role="progressbar" :aria-valuenow="progressPercent">
      <div class="progress-fill" :style="{ width: progressPercent + '%' }"></div>
      <span class="progress-text">{{ progressPercent }}%</span>
    </div>

    <div class="composer-row">
      <button
        type="button"
        class="attach-btn"
        :disabled="busy"
        :title="'添加附件'"
        @click="pickFile"
      >📎</button>

      <textarea
        ref="textareaEl"
        v-model="text"
        class="composer-input"
        :placeholder="placeholder"
        :disabled="busy"
        rows="2"
        @keydown.enter="handleEnter"
      ></textarea>

      <button
        type="button"
        class="send-btn"
        :disabled="!canSend"
        @click="doSubmit"
      >{{ busy ? '发送中...' : '发送' }}</button>
    </div>
  </div>
</template>

<style scoped>
.composer {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  border-top: 1px solid var(--color-divider);
  background: var(--color-surface);
}
.file-hidden {
  display: none;
}

.attachment-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  background: var(--color-bg);
  border: 1px dashed var(--color-border);
  border-radius: var(--radius-md);
  font-size: 13px;
  color: var(--color-text-secondary);
}
.attachment-name {
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.attachment-size {
  font-size: 12px;
  color: var(--color-text-hint);
}
.attachment-remove {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  border: none;
  background: var(--color-divider);
  color: var(--color-text-secondary);
  cursor: pointer;
  font-size: 14px;
  line-height: 1;
}
.attachment-remove:hover {
  background: var(--color-danger);
  color: #ffffff;
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
</style>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'

// D1-W22-02 快速捕获文本对话框
//
// 行为：
//   - 单 textarea + 发送按钮 + Cmd/Ctrl+Enter 直发
//   - 仅文本，不带附件（要发附件请用 NotesView FAB 「图片/视频/文件」走 fileFlow）
//   - 父级负责实际写入：emit('submit', text) → 调用方 await chatStore.send 等
//   - 成功时由父级通过 v-model:open=false 关闭；失败时父级保持 open，textarea 内容保留
//
// 注：组件本身不调 API，纯 UI；这样可以在 NotesView 与 ProfileView 等不同上下文复用。

const props = defineProps({
  open: { type: Boolean, default: false },
  busy: { type: Boolean, default: false },
  title: { type: String, default: '快速记录' },
  description: { type: String, default: '内容会发送到收集箱' }
})

const emit = defineEmits(['submit', 'cancel', 'update:open'])

const text = ref('')
const textareaEl = ref(null)

const canSend = computed(() => !props.busy && text.value.trim().length > 0)

watch(
  () => props.open,
  async (next) => {
    if (next) {
      // 自动聚焦 + 复位到上次输入（如果父级 reopens 同一对话框，保留草稿）
      await nextTick()
      textareaEl.value?.focus()
    }
  }
)

function handleKeydown(e) {
  // Cmd/Ctrl+Enter 直发；Enter 仍走换行（避免误发）
  if ((e.metaKey || e.ctrlKey) && e.key === 'Enter') {
    e.preventDefault()
    submit()
  } else if (e.key === 'Escape') {
    e.preventDefault()
    cancel()
  }
}

function submit() {
  if (!canSend.value) return
  emit('submit', text.value)
}

function cancel() {
  if (props.busy) return
  emit('cancel')
  emit('update:open', false)
}

function reset() {
  text.value = ''
}

defineExpose({ reset })
</script>

<template>
  <div v-if="open" class="modal-overlay" @click.self="cancel">
    <div class="modal" role="dialog" aria-label="快速记录">
      <header class="modal-header">
        <h2 class="modal-title">{{ title }}</h2>
        <button type="button" class="modal-close" :disabled="busy" @click="cancel">×</button>
      </header>
      <div class="modal-body">
        <p v-if="description" class="modal-desc">{{ description }}</p>
        <textarea
          ref="textareaEl"
          v-model="text"
          class="quick-textarea"
          :placeholder="'写点什么...（⌘/Ctrl + Enter 发送，Esc 取消）'"
          :disabled="busy"
          rows="6"
          @keydown="handleKeydown"
        ></textarea>
      </div>
      <footer class="modal-footer">
        <button type="button" class="btn-secondary" :disabled="busy" @click="cancel">取消</button>
        <button
          type="button"
          class="btn-primary"
          :disabled="!canSend"
          @click="submit"
        >{{ busy ? '发送中...' : '发送' }}</button>
      </footer>
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
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  width: 100%;
  max-width: 480px;
  display: flex;
  flex-direction: column;
  max-height: calc(100vh - 32px);
  overflow: hidden;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.25);
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
.modal-close:hover:not(:disabled) {
  color: var(--color-text-primary);
}
.modal-body {
  padding: 14px 18px;
  overflow-y: auto;
}
.modal-desc {
  margin: 0 0 10px 0;
  font-size: 13px;
  color: var(--color-text-secondary);
}
.quick-textarea {
  width: 100%;
  resize: vertical;
  min-height: 120px;
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  font-family: inherit;
  font-size: 14px;
  line-height: 1.5;
  color: var(--color-text-primary);
  background: var(--color-surface);
  outline: none;
  box-sizing: border-box;
}
.quick-textarea:focus {
  border-color: var(--color-primary);
}
.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 12px 18px;
  border-top: 1px solid var(--color-divider);
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
</style>

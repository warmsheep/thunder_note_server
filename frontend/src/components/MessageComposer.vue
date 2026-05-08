<script setup>
import { ref } from 'vue'

const props = defineProps({
  busy: { type: Boolean, default: false },
  placeholder: { type: String, default: '输入消息（Enter 发送，Shift+Enter 换行）' }
})

const emit = defineEmits(['submit'])

const text = ref('')
const textareaEl = ref(null)

function handleEnter(e) {
  if (e.shiftKey) return
  e.preventDefault()
  doSubmit()
}

function doSubmit() {
  const v = text.value
  if (props.busy) return
  if (!v.trim()) return
  emit('submit', v)
  // 不清空：父级在确认 send 成功后再调 clear()，失败时保留输入（W6-06）
}

function clear() {
  text.value = ''
}

defineExpose({ clear, focus: () => textareaEl.value?.focus() })
</script>

<template>
  <div class="composer">
    <textarea
      ref="textareaEl"
      v-model="text"
      class="composer-input"
      :placeholder="placeholder"
      :disabled="busy"
      rows="2"
      @keydown.enter="handleEnter"
    ></textarea>
    <div class="composer-actions">
      <button
        type="button"
        class="send-btn"
        :disabled="busy || !text.trim()"
        @click="doSubmit"
      >{{ busy ? '发送中...' : '发送' }}</button>
    </div>
  </div>
</template>

<style scoped>
.composer {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 12px;
  border-top: 1px solid var(--color-divider);
  background: var(--color-surface);
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
.composer-actions {
  display: flex;
  align-items: flex-end;
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

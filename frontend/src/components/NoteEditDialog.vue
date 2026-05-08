<script setup>
import { ref, watch } from 'vue'
import { validateCreateForm, validateUpdateForm } from '../utils/flashNoteValidators'

// 创建/编辑闪记弹窗
// - props.open: 控制可见
// - props.mode: 'create' | 'edit'
// - props.initial: 编辑时的初始字段
// - emit submit({ title, icon })
// - emit cancel

const props = defineProps({
  open: { type: Boolean, default: false },
  mode: { type: String, default: 'create' }, // 'create' | 'edit'
  initial: { type: Object, default: () => ({}) },
  busy: { type: Boolean, default: false }
})

const emit = defineEmits(['submit', 'cancel'])

const title = ref('')
const icon = ref('')
const errorMessage = ref('')

watch(
  () => [props.open, props.initial],
  ([open]) => {
    if (open) {
      title.value = props.initial?.title || ''
      icon.value = props.initial?.icon || ''
      errorMessage.value = ''
    }
  },
  { immediate: true }
)

function handleSubmit() {
  errorMessage.value = ''
  const payload = { title: title.value.trim(), icon: icon.value.trim() }
  const result = props.mode === 'edit' ? validateUpdateForm(payload) : validateCreateForm(payload)
  if (!result.ok) {
    errorMessage.value = result.message
    return
  }
  emit('submit', payload)
}
</script>

<template>
  <Teleport to="body">
    <transition name="fade">
      <div v-if="open" class="dialog-mask" @click.self="emit('cancel')">
        <section class="dialog-card" role="dialog" aria-modal="true">
          <header class="dialog-header">
            <h2 class="dialog-title">{{ mode === 'edit' ? '编辑闪记' : '新建闪记' }}</h2>
          </header>
          <form class="dialog-form" @submit.prevent="handleSubmit">
            <label class="field">
              <span>名称</span>
              <input
                v-model="title"
                type="text"
                maxlength="500"
                :disabled="busy"
                placeholder="给这条闪记起个名字"
                required
              />
            </label>
            <label class="field">
              <span>图标（可选）</span>
              <input
                v-model="icon"
                type="text"
                maxlength="64"
                :disabled="busy"
                placeholder="例如 📝、💡、📚"
              />
            </label>
            <p v-if="errorMessage" class="field-error" role="alert">{{ errorMessage }}</p>
            <footer class="dialog-actions">
              <button
                type="button"
                class="btn btn-secondary"
                :disabled="busy"
                @click="emit('cancel')"
              >取消</button>
              <button
                type="submit"
                class="btn btn-primary"
                :disabled="busy"
              >{{ busy ? '保存中...' : (mode === 'edit' ? '保存' : '创建') }}</button>
            </footer>
          </form>
        </section>
      </div>
    </transition>
  </Teleport>
</template>

<style scoped>
.dialog-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 900;
  padding: 16px;
}
.dialog-card {
  width: 100%;
  max-width: 420px;
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  padding: 24px;
  box-shadow: var(--shadow-md);
}
.dialog-header {
  margin-bottom: 16px;
}
.dialog-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-primary);
}
.dialog-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 14px;
  color: var(--color-text-primary);
}
.field input {
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  font-size: 14px;
  outline: none;
  background: var(--color-surface);
  color: var(--color-text-primary);
}
.field input:focus {
  border-color: var(--color-primary);
}
.field input:disabled {
  background: var(--color-bg);
  cursor: not-allowed;
}
.field-error {
  margin: 0;
  padding: 8px 12px;
  background: var(--color-danger-bg);
  color: var(--color-danger);
  border-radius: var(--radius-sm);
  font-size: 13px;
}
.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
}
.btn {
  padding: 8px 16px;
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  background: transparent;
  font-size: 14px;
  cursor: pointer;
}
.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.btn-secondary {
  border-color: var(--color-border);
  color: var(--color-text-primary);
}
.btn-secondary:hover:not(:disabled) {
  background: var(--color-bg);
}
.btn-primary {
  background: var(--color-primary);
  color: #ffffff;
}
.btn-primary:hover:not(:disabled) {
  background: var(--color-primary-dark);
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.15s;
}
</style>

<script setup>
defineProps({
  open: { type: Boolean, default: false },
  title: { type: String, default: '确认' },
  message: { type: String, default: '' },
  confirmLabel: { type: String, default: '确定' },
  cancelLabel: { type: String, default: '取消' },
  danger: { type: Boolean, default: false },
  busy: { type: Boolean, default: false }
})

const emit = defineEmits(['confirm', 'cancel'])
</script>

<template>
  <Teleport to="body">
    <transition name="fade">
      <div v-if="open" class="dialog-mask" @click.self="emit('cancel')">
        <section class="dialog-card" role="alertdialog" aria-modal="true">
          <header class="dialog-header">
            <h2 class="dialog-title">{{ title }}</h2>
          </header>
          <p v-if="message" class="dialog-message">{{ message }}</p>
          <footer class="dialog-actions">
            <button
              type="button"
              class="btn btn-secondary"
              :disabled="busy"
              @click="emit('cancel')"
            >{{ cancelLabel }}</button>
            <button
              type="button"
              class="btn"
              :class="danger ? 'btn-danger' : 'btn-primary'"
              :disabled="busy"
              @click="emit('confirm')"
            >{{ busy ? '处理中...' : confirmLabel }}</button>
          </footer>
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
}
.dialog-card {
  width: 100%;
  max-width: 360px;
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  padding: 24px;
  box-shadow: var(--shadow-md);
}
.dialog-header {
  margin-bottom: 12px;
}
.dialog-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-primary);
}
.dialog-message {
  margin: 0 0 20px 0;
  font-size: 14px;
  color: var(--color-text-secondary);
  line-height: 1.5;
}
.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
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
.btn-danger {
  background: var(--color-danger);
  color: #ffffff;
}
.btn-danger:hover:not(:disabled) {
  filter: brightness(0.95);
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

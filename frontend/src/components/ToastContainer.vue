<script setup>
import { useToast } from '../composables/useToast'

const { list, removeToast } = useToast()
</script>

<template>
  <Teleport to="body">
    <div class="toast-container" role="region" aria-label="通知">
      <transition-group name="toast" tag="div" class="toast-stack">
        <div
          v-for="t in list"
          :key="t.id"
          class="toast-item"
          :class="`toast-${t.type}`"
          role="status"
          @click="removeToast(t.id)"
        >
          {{ t.message }}
        </div>
      </transition-group>
    </div>
  </Teleport>
</template>

<style scoped>
.toast-container {
  position: fixed;
  top: 24px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 1000;
  pointer-events: none;
}
.toast-stack {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}
.toast-item {
  pointer-events: auto;
  cursor: pointer;
  padding: 10px 18px;
  border-radius: var(--radius-md);
  font-size: 14px;
  box-shadow: var(--shadow-md);
  max-width: 80vw;
  word-break: break-word;
  background: rgba(31, 41, 55, 0.92);
  color: #ffffff;
}
.toast-success {
  background: var(--color-primary);
}
.toast-error {
  background: var(--color-danger);
}
.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateY(-12px);
}
.toast-enter-active,
.toast-leave-active {
  transition: opacity 0.18s, transform 0.18s;
}
</style>

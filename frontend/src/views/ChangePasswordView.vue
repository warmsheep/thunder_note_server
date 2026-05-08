<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { changePassword } from '../api/auth'
import { useToast } from '../composables/useToast'

// D1-W15 修改密码
// 表单：当前密码 / 新密码 / 确认新密码
// 校验对齐后端 ChangePasswordRequest：
//   - currentPassword @NotBlank
//   - newPassword @NotBlank + @Size(min = 6)
// 前端额外校验：
//   - newPassword !== currentPassword（避免无意义修改）
//   - confirmPassword === newPassword
// 成功后 toast，清空表单，回 /profile（后端不轮替 token，旧 access token 仍可用）

const router = useRouter()
const { showSuccess, showError } = useToast()

const form = ref({
  currentPassword: '',
  newPassword: '',
  confirmPassword: ''
})
const submitting = ref(false)
const fieldErrors = ref({
  currentPassword: '',
  newPassword: '',
  confirmPassword: ''
})

function clearError(field) {
  if (fieldErrors.value[field]) {
    fieldErrors.value[field] = ''
  }
}

const canSubmit = computed(
  () =>
    !submitting.value &&
    form.value.currentPassword.length > 0 &&
    form.value.newPassword.length >= 6 &&
    form.value.confirmPassword === form.value.newPassword &&
    form.value.newPassword !== form.value.currentPassword
)

function validateLocal() {
  const errs = { currentPassword: '', newPassword: '', confirmPassword: '' }
  if (!form.value.currentPassword) {
    errs.currentPassword = '请输入当前密码'
  }
  if (!form.value.newPassword) {
    errs.newPassword = '请输入新密码'
  } else if (form.value.newPassword.length < 6) {
    errs.newPassword = '新密码至少 6 位'
  } else if (form.value.newPassword === form.value.currentPassword) {
    errs.newPassword = '新密码不能与当前密码相同'
  }
  if (!form.value.confirmPassword) {
    errs.confirmPassword = '请再次输入新密码'
  } else if (form.value.confirmPassword !== form.value.newPassword) {
    errs.confirmPassword = '两次输入的新密码不一致'
  }
  fieldErrors.value = errs
  return !errs.currentPassword && !errs.newPassword && !errs.confirmPassword
}

async function handleSubmit() {
  if (!validateLocal()) return
  submitting.value = true
  try {
    await changePassword({
      currentPassword: form.value.currentPassword,
      newPassword: form.value.newPassword
    })
    showSuccess('密码修改成功')
    form.value = { currentPassword: '', newPassword: '', confirmPassword: '' }
    fieldErrors.value = { currentPassword: '', newPassword: '', confirmPassword: '' }
    router.replace({ path: '/profile' })
  } catch (e) {
    const msg = e?.serverMessage || e?.message || '修改失败'
    // 后端"当前密码错误"对齐到对应字段错位
    if (/current password/i.test(msg) || /当前密码/i.test(msg)) {
      fieldErrors.value.currentPassword = '当前密码不正确'
    } else {
      showError(msg)
    }
  } finally {
    submitting.value = false
  }
}

function handleCancel() {
  router.back()
}
</script>

<template>
  <div class="change-password-page">
    <header class="page-header">
      <h1 class="page-title">修改密码</h1>
      <p class="page-sub">修改后旧的登录态仍会保持到当前 token 过期</p>
    </header>

    <form class="card" @submit.prevent="handleSubmit" novalidate>
      <div class="form-row">
        <label class="form-label" for="currentPassword">当前密码</label>
        <input
          id="currentPassword"
          v-model="form.currentPassword"
          type="password"
          class="form-input"
          autocomplete="current-password"
          :disabled="submitting"
          :aria-invalid="!!fieldErrors.currentPassword"
          @input="clearError('currentPassword')"
        />
        <p v-if="fieldErrors.currentPassword" class="field-error">
          {{ fieldErrors.currentPassword }}
        </p>
      </div>

      <div class="form-row">
        <label class="form-label" for="newPassword">新密码</label>
        <input
          id="newPassword"
          v-model="form.newPassword"
          type="password"
          class="form-input"
          autocomplete="new-password"
          :disabled="submitting"
          :aria-invalid="!!fieldErrors.newPassword"
          @input="clearError('newPassword')"
        />
        <p v-if="fieldErrors.newPassword" class="field-error">
          {{ fieldErrors.newPassword }}
        </p>
        <p v-else class="field-hint">至少 6 位，建议混合大小写字母与数字</p>
      </div>

      <div class="form-row">
        <label class="form-label" for="confirmPassword">确认新密码</label>
        <input
          id="confirmPassword"
          v-model="form.confirmPassword"
          type="password"
          class="form-input"
          autocomplete="new-password"
          :disabled="submitting"
          :aria-invalid="!!fieldErrors.confirmPassword"
          @input="clearError('confirmPassword')"
        />
        <p v-if="fieldErrors.confirmPassword" class="field-error">
          {{ fieldErrors.confirmPassword }}
        </p>
      </div>

      <div class="form-actions">
        <button
          type="button"
          class="btn-secondary"
          :disabled="submitting"
          @click="handleCancel"
        >取消</button>
        <button
          type="submit"
          class="btn-primary"
          :disabled="!canSubmit"
        >{{ submitting ? '提交中...' : '保存' }}</button>
      </div>
    </form>
  </div>
</template>

<style scoped>
.change-password-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: 480px;
  margin: 0 auto;
}
.page-header {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.page-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--color-text-primary);
}
.page-sub {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-hint);
}

.card {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 20px;
  background: var(--color-surface);
  border: 1px solid var(--color-divider);
  border-radius: var(--radius-lg);
}
.form-row {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.form-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-primary);
}
.form-input {
  padding: 8px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  font-size: 14px;
  outline: none;
  background: var(--color-surface);
  color: var(--color-text-primary);
}
.form-input:focus {
  border-color: var(--color-primary);
}
.form-input[aria-invalid="true"] {
  border-color: var(--color-danger);
}
.form-input:disabled {
  background: var(--color-bg);
  cursor: not-allowed;
}
.field-error {
  margin: 0;
  color: var(--color-danger);
  font-size: 12px;
}
.field-hint {
  margin: 0;
  color: var(--color-text-hint);
  font-size: 12px;
}

.form-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}
.btn-primary {
  padding: 8px 18px;
  border: none;
  border-radius: var(--radius-md);
  background: var(--color-primary);
  color: #ffffff;
  font-size: 14px;
  cursor: pointer;
}
.btn-primary:hover:not(:disabled) {
  background: var(--color-primary-dark);
}
.btn-primary:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.btn-secondary {
  padding: 8px 18px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  color: var(--color-text-secondary);
  font-size: 14px;
  cursor: pointer;
}
.btn-secondary:hover:not(:disabled) {
  border-color: var(--color-primary);
  color: var(--color-primary);
}
</style>

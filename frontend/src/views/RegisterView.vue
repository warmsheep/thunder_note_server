<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { register as registerApi } from '../api/auth'
import { validateRegisterForm } from '../utils/validators'
import logoRegister from '../assets/icons/logo-login.png'

const router = useRouter()

const username = ref('')
const email = ref('')
const password = ref('')
const confirmPassword = ref('')
const errorMessage = ref('')
const successMessage = ref('')
const submitting = ref(false)

async function handleSubmit() {
  errorMessage.value = ''
  successMessage.value = ''
  const result = validateRegisterForm({
    username: username.value,
    email: email.value,
    password: password.value,
    confirmPassword: confirmPassword.value
  })
  if (!result.ok) {
    errorMessage.value = result.message
    return
  }
  submitting.value = true
  try {
    await registerApi({
      username: username.value.trim(),
      email: email.value.trim(),
      password: password.value
    })
    successMessage.value = '注册成功，正在跳转到登录页...'
    setTimeout(() => {
      router.replace({ path: '/login' })
    }, 800)
  } catch (e) {
    errorMessage.value = e?.serverMessage || e?.message || '注册失败，请重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="auth-page">
    <div class="auth-scroll">
      <img class="auth-logo" :src="logoRegister" alt="闪记" />

      <h1 class="auth-title">注册账号</h1>
      <p class="auth-subtitle">创建您的闪记账号</p>

      <form class="auth-form" @submit.prevent="handleSubmit">
        <input
          v-model="username"
          class="auth-input"
          type="text"
          placeholder="请输入用户名"
          autocomplete="username"
          autocapitalize="off"
          autocorrect="off"
          spellcheck="false"
          :disabled="submitting"
          required
        />

        <input
          v-model="email"
          class="auth-input"
          type="email"
          placeholder="请输入邮箱"
          autocomplete="email"
          :disabled="submitting"
          required
        />

        <input
          v-model="password"
          class="auth-input"
          type="password"
          placeholder="请输入密码"
          autocomplete="new-password"
          :disabled="submitting"
          required
        />

        <input
          v-model="confirmPassword"
          class="auth-input"
          type="password"
          placeholder="确认密码"
          autocomplete="new-password"
          :disabled="submitting"
          required
        />

        <button type="submit" class="auth-submit" :disabled="submitting">
          {{ submitting ? '注册中...' : '注册' }}
        </button>

        <p v-if="errorMessage" class="auth-error" role="alert">{{ errorMessage }}</p>
        <p v-if="successMessage" class="auth-success" role="status">{{ successMessage }}</p>

        <router-link to="/login" class="auth-link">
          已有账号？立即登录
        </router-link>
      </form>
    </div>
  </main>
</template>

<style scoped>
/* 对齐 Android fragment_register.xml + LoginView.vue 一致视觉 */
.auth-page {
  min-height: 100vh;
  background: var(--color-surface);
  display: flex;
  align-items: stretch;
  justify-content: center;
}
.auth-scroll {
  width: 100%;
  max-width: 420px;
  padding: 40px 24px 20px 24px;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  box-sizing: border-box;
}
.auth-logo {
  display: block;
  width: 120px;
  height: 120px;
  margin: 0 auto;
  object-fit: contain;
  background: var(--color-primary-light);
  border-radius: 16px;
  padding: 10px;
}
.auth-title {
  margin: 30px 0 0 0;
  text-align: center;
  font-size: 24px;
  font-weight: 700;
  color: var(--color-text-primary);
}
.auth-subtitle {
  margin: 8px 0 0 0;
  text-align: center;
  font-size: 14px;
  color: var(--color-text-secondary);
}
.auth-form {
  margin-top: 40px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.auth-input {
  height: 50px;
  padding: 0 14px;
  background: var(--color-bg);
  border: 1px solid var(--color-divider);
  border-radius: 8px;
  font-size: 15px;
  color: var(--color-text-primary);
  outline: none;
  transition: border-color 0.15s, background 0.15s;
}
.auth-input::placeholder {
  color: var(--color-text-hint);
}
.auth-input:focus {
  border-color: var(--color-primary);
  background: var(--color-surface);
}
.auth-input:disabled {
  background: var(--color-bg);
  color: var(--color-text-secondary);
  cursor: not-allowed;
}
.auth-submit {
  margin-top: 2px;
  height: 50px;
  border: none;
  border-radius: 8px;
  background: var(--color-primary);
  color: #ffffff;
  font-size: 16px;
  font-weight: 700;
  cursor: pointer;
  transition: background 0.15s;
}
.auth-submit:hover:not(:disabled) {
  background: var(--color-primary-dark);
}
.auth-submit:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.auth-error {
  margin: 0;
  font-size: 13px;
  color: var(--color-danger);
  text-align: center;
}
.auth-success {
  margin: 0;
  padding: 8px 12px;
  background: var(--color-success-bg);
  color: var(--color-success);
  border-radius: 6px;
  font-size: 13px;
  text-align: center;
}
.auth-link {
  margin-top: 4px;
  text-align: center;
  font-size: 14px;
  color: var(--color-primary);
  text-decoration: none;
  padding: 8px 0;
}
.auth-link:hover {
  text-decoration: underline;
}

/* 移动端：缩小 padding 以更接近 Android 手机视觉 */
@media (max-width: 480px) {
  .auth-scroll {
    padding: 32px 20px 16px 20px;
  }
  .auth-logo {
    width: 100px;
    height: 100px;
  }
  .auth-title {
    margin-top: 24px;
    font-size: 22px;
  }
  .auth-form {
    margin-top: 32px;
  }
}
</style>

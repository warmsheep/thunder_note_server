<script setup>
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { validateLoginForm } from '../utils/validators'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const username = ref('')
const password = ref('')
const errorMessage = ref('')
const submitting = ref(false)

async function handleSubmit() {
  errorMessage.value = ''
  const result = validateLoginForm({
    username: username.value,
    password: password.value
  })
  if (!result.ok) {
    errorMessage.value = result.message
    return
  }
  submitting.value = true
  try {
    await authStore.login({
      username: username.value.trim(),
      password: password.value
    })
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    router.replace(redirect)
  } catch (e) {
    errorMessage.value = e?.serverMessage || e?.message || '登录失败，请重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="auth-page">
    <section class="auth-card">
      <h1 class="auth-title">登录闪记</h1>
      <p class="auth-subtitle">使用用户名和密码登录</p>

      <form class="auth-form" @submit.prevent="handleSubmit">
        <label class="auth-field">
          <span>用户名</span>
          <input
            v-model="username"
            type="text"
            autocomplete="username"
            :disabled="submitting"
            required
          />
        </label>

        <label class="auth-field">
          <span>密码</span>
          <input
            v-model="password"
            type="password"
            autocomplete="current-password"
            :disabled="submitting"
            required
          />
        </label>

        <p v-if="errorMessage" class="auth-error" role="alert">{{ errorMessage }}</p>

        <button type="submit" class="auth-submit" :disabled="submitting">
          {{ submitting ? '登录中...' : '登录' }}
        </button>
      </form>

      <p class="auth-footer">
        还没有账号？
        <router-link to="/register">去注册</router-link>
      </p>
    </section>
  </main>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}
.auth-card {
  width: 100%;
  max-width: 400px;
  background: #ffffff;
  padding: 32px;
  border-radius: 12px;
  border: 1px solid #e5e7eb;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}
.auth-title {
  margin: 0 0 8px 0;
  font-size: 22px;
  font-weight: 600;
  color: #111827;
}
.auth-subtitle {
  margin: 0 0 24px 0;
  color: #6b7280;
  font-size: 14px;
}
.auth-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.auth-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 14px;
  color: #374151;
}
.auth-field input {
  padding: 10px 12px;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  font-size: 14px;
  outline: none;
  transition: border-color 0.15s;
}
.auth-field input:focus {
  border-color: #6366f1;
}
.auth-field input:disabled {
  background: #f3f4f6;
  cursor: not-allowed;
}
.auth-error {
  margin: 0;
  padding: 8px 12px;
  background: #fef2f2;
  color: #b91c1c;
  border-radius: 6px;
  font-size: 13px;
}
.auth-submit {
  margin-top: 8px;
  padding: 10px 16px;
  border: none;
  border-radius: 8px;
  background: #4f46e5;
  color: #ffffff;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.15s;
}
.auth-submit:hover:not(:disabled) {
  background: #4338ca;
}
.auth-submit:disabled {
  background: #a5b4fc;
  cursor: not-allowed;
}
.auth-footer {
  margin: 24px 0 0 0;
  text-align: center;
  font-size: 14px;
  color: #6b7280;
}
.auth-footer a {
  color: #4f46e5;
  text-decoration: none;
}
.auth-footer a:hover {
  text-decoration: underline;
}
</style>

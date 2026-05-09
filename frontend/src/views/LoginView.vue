<script setup>
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { validateLoginForm } from '../utils/validators'
import logoLogin from '../assets/icons/logo-login.png'

// D1-W28-08 登录页参照 Android `fragment_login.xml` 视觉重构：
//   - 顶部居中 logo（复用 Android `ic_page_logo_login.png`）
//   - 标题「欢迎回来」+ 副标题「登录以继续使用闪记」
//   - 用户名 / 密码 走圆角 input
//   - 主操作按钮全宽圆角（与 Android `bg_button_primary_rounded` 一致视觉）
//   - 「还没有账号？立即注册」走主色文本链接
//   - Android 字串与 dimens 不直接复用，但文案一致；Web 用相同视觉密度

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
    <div class="auth-scroll">
      <img class="auth-logo" :src="logoLogin" alt="闪记" />

      <h1 class="auth-title">欢迎回来</h1>
      <p class="auth-subtitle">登录以继续使用闪记</p>

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
          v-model="password"
          class="auth-input"
          type="password"
          placeholder="请输入密码"
          autocomplete="current-password"
          :disabled="submitting"
          required
        />

        <button type="submit" class="auth-submit" :disabled="submitting">
          {{ submitting ? '登录中...' : '登录' }}
        </button>

        <p v-if="errorMessage" class="auth-error" role="alert">{{ errorMessage }}</p>

        <router-link to="/register" class="auth-link">
          还没有账号？立即注册
        </router-link>
      </form>
    </div>
  </main>
</template>

<style scoped>
/* D1-W28-08 与 Android `fragment_login.xml` 对齐：
   - 整页 surface 底色，单列 scroll 容器
   - logo 160 × 160 居中，title 30dp 间距，subtitle 8dp 间距
   - input 50dp 高 + 8 圆角，主操作按钮全宽 50dp + 8 圆角 */
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
  width: 160px;
  height: 160px;
  margin: 0 auto;
  object-fit: contain;
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
    width: 132px;
    height: 132px;
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

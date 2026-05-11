<script setup>
import { ref } from 'vue'
import apiClient from '../api/client'

const healthState = ref({ status: 'idle', detail: '' })

async function checkBackend() {
  healthState.value = { status: 'loading', detail: '' }
  try {
    // 走同源 /actuator/health；当前 SecurityConfig 已对其放行
    const resp = await apiClient.raw.get('/actuator/health')
    healthState.value = {
      status: 'ok',
      detail: typeof resp.data === 'object' ? JSON.stringify(resp.data) : String(resp.data)
    }
  } catch (e) {
    healthState.value = { status: 'error', detail: e?.message || '请求失败' }
  }
}
</script>

<template>
  <main class="baseline">
    <h1>闪记 Web · 工程基线</h1>
    <p class="hint">这是 D1-W0/W1 阶段的占位页面。后续 W3/W4 任务会替换为登录与主壳层。</p>
    <button type="button" @click="checkBackend">检查后端连通性</button>
    <p v-if="healthState.status === 'loading'">检查中...</p>
    <p v-else-if="healthState.status === 'ok'" class="ok">后端可达：{{ healthState.detail }}</p>
    <p v-else-if="healthState.status === 'error'" class="error">后端不可达：{{ healthState.detail }}</p>
  </main>
</template>

<style scoped>
.baseline {
  max-width: 560px;
  margin: 64px auto;
  padding: 24px;
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Helvetica Neue', sans-serif;
}
.hint {
  color: #6b7280;
  margin-bottom: 16px;
}
.ok {
  color: #047857;
  word-break: break-all;
}
.error {
  color: #b91c1c;
  word-break: break-all;
}
button {
  padding: 8px 16px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  background: #ffffff;
  cursor: pointer;
}
button:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}
</style>

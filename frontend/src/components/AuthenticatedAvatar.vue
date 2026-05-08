<script setup>
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import { fetchAsObjectUrl } from '../api/files'
import { extractObjectName, needsAuthenticatedFetch } from '../utils/avatarHelpers'

// D1-W11 鉴权头像
// - 后端 avatar 字段是 `${origin}/api/files/download?objectName=...` 形式
// - 浏览器 <img src> 直接走会因为没有 Authorization 拿到 401，必须 fetch+blob
// - 公开 CDN 的 URL（不命中 needsAuthenticatedFetch）则当作普通图片直接走
// - 失败或没头像时显示 fallback 字符（昵称首字符）

const props = defineProps({
  avatar: { type: String, default: '' },
  fallback: { type: String, default: '?' },
  size: { type: Number, default: 56 }
})

const blobUrl = ref(null)
const failed = ref(false)
const loading = ref(false)

const sizePx = computed(() => `${props.size}px`)
const fontPx = computed(() => `${Math.round(props.size * 0.45)}px`)
const directSrc = computed(() => (props.avatar && !needsAuthenticatedFetch(props.avatar) ? props.avatar : ''))
const showImage = computed(() => Boolean(blobUrl.value || directSrc.value) && !failed.value)
const fallbackChar = computed(() => (props.fallback || '?').slice(0, 1))

function dispose() {
  if (blobUrl.value) {
    try { URL.revokeObjectURL(blobUrl.value) } catch (_e) { /* ignore */ }
    blobUrl.value = null
  }
}

async function load() {
  dispose()
  failed.value = false
  if (!props.avatar) return
  if (!needsAuthenticatedFetch(props.avatar)) return
  const objectName = extractObjectName(props.avatar)
  if (!objectName) return
  loading.value = true
  try {
    blobUrl.value = await fetchAsObjectUrl(objectName)
  } catch (_e) {
    failed.value = true
  } finally {
    loading.value = false
  }
}

watch(() => props.avatar, () => load(), { immediate: true })
onBeforeUnmount(dispose)
</script>

<template>
  <div class="auth-avatar" :style="{ width: sizePx, height: sizePx, fontSize: fontPx }">
    <img
      v-if="showImage"
      :src="blobUrl || directSrc"
      class="img"
      alt="avatar"
      @error="failed = true"
    />
    <span v-else class="ph">{{ fallbackChar }}</span>
  </div>
</template>

<style scoped>
.auth-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--color-primary);
  color: #ffffff;
  font-weight: 600;
  overflow: hidden;
  flex-shrink: 0;
}
.img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.ph {
  line-height: 1;
}
</style>

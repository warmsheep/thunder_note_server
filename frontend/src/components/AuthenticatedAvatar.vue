<script setup>
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import { fetchAsObjectUrl } from '../api/files'
import { extractObjectName, needsAuthenticatedFetch, isEmojiAvatar } from '../utils/avatarHelpers'

// D1-W11 / W23 鉴权头像
// - 后端 avatar 字段的四种取值：
//   1) `${origin}/api/files/download?objectName=...`（鉴权下载）→ fetch+blob 显示
//   2) 普通 CDN URL（https开头但非识别的 download）→ 直接 <img src>
//   3) objectName（userId/uuid.ext）→ fetch+blob
//   4) emoji / 短字符串（W23-01 新增）→ 直接渲染作为文本
// - 图片加载失败或没有头像时显示 fallback 字符（昵称首字符）

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

// emoji 头像：不走 <img>，直接以文本显示原字符串
const emojiText = computed(() => (isEmojiAvatar(props.avatar) ? String(props.avatar).trim() : ''))
const showEmoji = computed(() => Boolean(emojiText.value) && !failed.value)

// 普通 URL（非禁是非 emoji，且不需鉴权请求） → 直接 <img src>
const directSrc = computed(() =>
  props.avatar && !isEmojiAvatar(props.avatar) && !needsAuthenticatedFetch(props.avatar)
    ? props.avatar
    : ''
)
const showImage = computed(() => !emojiText.value && Boolean(blobUrl.value || directSrc.value) && !failed.value)
// emoji 先于 fallback；两者都无时才用 props.fallback 首字符
const fallbackChar = computed(() => (props.fallback || '?').slice(0, 1))
const emojiFontPx = computed(() => `${Math.round(props.size * 0.6)}px`)

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
  // emoji 分支：无需网络请求
  if (isEmojiAvatar(props.avatar)) return
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
    <span
      v-else-if="showEmoji"
      class="emoji"
      :style="{ fontSize: emojiFontPx }"
      aria-hidden="true"
    >{{ emojiText }}</span>
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
.emoji {
  line-height: 1;
  /* emoji 需要透明背景，避免主色的白色默认背景混淆彩色 emoji */
  background: var(--color-surface);
  color: var(--color-text-primary);
  width: 100%;
  height: 100%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
</style>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useProfileStore } from '../stores/profile'
import { useToast } from '../composables/useToast'
import { uploadFile } from '../api/files'
import { buildAvatarUrl } from '../utils/avatarHelpers'
import LoadingState from '../components/LoadingState.vue'
import ErrorState from '../components/ErrorState.vue'
import AuthenticatedAvatar from '../components/AuthenticatedAvatar.vue'

// D1-W11 个人资料与设置
// - W11-01 进页拉一次 POST /api/users/profile
// - W11-02 编辑昵称/简介，PUT /api/users/profile
// - W11-03 上传头像：file → uploadFile 拿 objectName → 包成 download URL → PUT /api/users/avatar
// - W11-04 设置区：展示 API 基址、客户端版本、登出，不展示任何 token

const router = useRouter()
const authStore = useAuthStore()
const profileStore = useProfileStore()
const { showSuccess, showError } = useToast()

const avatarInputEl = ref(null)
const avatarUploading = ref(false)
const avatarProgress = ref(0)

const editing = ref(false)
const editForm = ref({ nickname: '', bio: '' })
const editError = ref('')

const NICK_MAX = 32
const BIO_MAX = 200
const AVATAR_MAX_BYTES = 5 * 1024 * 1024 // 5 MB

const user = computed(() => authStore.user || {})
const profile = computed(() => profileStore.profile || {})
const fallbackChar = computed(() => (profileStore.nickname || user.value.username || '?').slice(0, 1).toUpperCase())

const apiBase = computed(() => {
  // 在浏览器环境下，apiClient 用相对路径；展示时使用当前 origin 让用户看到完整地址
  if (typeof window !== 'undefined' && window.location && window.location.origin) {
    return window.location.origin
  }
  return '(unknown)'
})

const clientVersion = import.meta.env?.VITE_APP_VERSION || 'd1.6-dev'

const isLoadingFirst = computed(() => profileStore.loading && !profileStore.loaded)
const showError_ = computed(() => Boolean(profileStore.error) && !profileStore.loaded)

onMounted(async () => {
  if (!profileStore.loaded) {
    try {
      await profileStore.fetch()
    } catch (_e) {
      // store.error 已设
    }
  }
})

function startEdit() {
  editForm.value = {
    nickname: profileStore.nickname || '',
    bio: profileStore.bio || ''
  }
  editError.value = ''
  editing.value = true
}

function cancelEdit() {
  editing.value = false
  editError.value = ''
}

function validate() {
  const nick = editForm.value.nickname?.trim() ?? ''
  const bio = editForm.value.bio ?? ''
  if (nick && nick.length > NICK_MAX) {
    return `昵称长度不能超过 ${NICK_MAX} 个字符`
  }
  if (bio && bio.length > BIO_MAX) {
    return `简介长度不能超过 ${BIO_MAX} 个字符`
  }
  return ''
}

async function saveEdit() {
  const err = validate()
  if (err) {
    editError.value = err
    return
  }
  editError.value = ''
  try {
    await profileStore.saveProfile({
      nickname: editForm.value.nickname?.trim() || null,
      bio: editForm.value.bio?.trim() || null
    })
    // 同步 auth.user 中的 nickname，让 MainShell/Toast 等位置 displayName 立刻更新
    authStore.patchUser({ nickname: profileStore.nickname || null })
    showSuccess('已保存')
    editing.value = false
  } catch (e) {
    editError.value = e?.serverMessage || e?.message || '保存失败'
  }
}

function pickAvatar() {
  avatarInputEl.value?.click()
}

async function onAvatarChange(e) {
  const f = e.target.files && e.target.files[0]
  if (avatarInputEl.value) avatarInputEl.value.value = ''
  if (!f) return
  if (!f.type || !f.type.startsWith('image/')) {
    showError('请选择图片文件')
    return
  }
  if (f.size > AVATAR_MAX_BYTES) {
    showError(`图片大小不能超过 ${AVATAR_MAX_BYTES / 1024 / 1024} MB`)
    return
  }
  avatarUploading.value = true
  avatarProgress.value = 0
  try {
    const result = await uploadFile(f, {
      onUploadProgress: (ev) => {
        if (ev && ev.total > 0) avatarProgress.value = ev.loaded / ev.total
      }
    })
    const objectName = result?.objectName
    if (!objectName) {
      throw new Error('上传未返回有效结果')
    }
    const avatarUrl = buildAvatarUrl(objectName)
    await profileStore.saveAvatar(avatarUrl)
    authStore.patchUser({ avatar: avatarUrl })
    showSuccess('头像已更新')
  } catch (err) {
    showError(err?.serverMessage || err?.message || '头像上传失败')
  } finally {
    avatarUploading.value = false
    avatarProgress.value = 0
  }
}

async function handleLogout() {
  try {
    await authStore.logout()
  } finally {
    profileStore.reset()
    router.push({ name: 'login' })
  }
}

function handleRetry() {
  profileStore.fetch().catch(() => {})
}

const avatarPercent = computed(() => Math.round(avatarProgress.value * 100))
</script>

<template>
  <div class="profile-page">
    <LoadingState v-if="isLoadingFirst" text="加载资料中..." />
    <ErrorState
      v-else-if="showError_"
      :message="profileStore.error"
      @retry="handleRetry"
    />
    <template v-else>
      <section class="card">
        <header class="card-header">
          <div class="avatar-wrap">
            <AuthenticatedAvatar
              :avatar="profileStore.avatar"
              :fallback="fallbackChar"
              :size="72"
            />
            <button
              type="button"
              class="avatar-edit"
              :disabled="avatarUploading"
              @click="pickAvatar"
            >{{ avatarUploading ? `${avatarPercent}%` : '更换头像' }}</button>
            <input
              ref="avatarInputEl"
              type="file"
              accept="image/*"
              class="hidden-input"
              @change="onAvatarChange"
            />
          </div>
          <div class="meta">
            <p class="name">{{ profileStore.nickname || user.username || '未设置昵称' }}</p>
            <p v-if="user.username" class="caption">用户名：{{ user.username }}</p>
            <p v-if="user.email" class="caption">邮箱：{{ user.email }}</p>
          </div>
          <button
            v-if="!editing"
            type="button"
            class="btn-secondary"
            @click="startEdit"
          >编辑资料</button>
        </header>

        <div class="card-body">
          <template v-if="!editing">
            <p class="bio-label">个人简介</p>
            <p class="bio-text">{{ profileStore.bio || '（还没有简介，点击右上角编辑添加）' }}</p>
          </template>
          <form v-else class="edit-form" @submit.prevent="saveEdit">
            <label class="field">
              <span class="field-label">昵称（≤ {{ NICK_MAX }} 字）</span>
              <input
                v-model="editForm.nickname"
                type="text"
                :maxlength="NICK_MAX"
                class="field-input"
                placeholder="给自己起个名字"
              />
            </label>
            <label class="field">
              <span class="field-label">简介（≤ {{ BIO_MAX }} 字）</span>
              <textarea
                v-model="editForm.bio"
                :maxlength="BIO_MAX"
                rows="3"
                class="field-input"
                placeholder="一句话介绍自己"
              ></textarea>
            </label>
            <p v-if="editError" class="form-error">{{ editError }}</p>
            <div class="form-actions">
              <button
                type="button"
                class="btn-secondary"
                :disabled="profileStore.saving"
                @click="cancelEdit"
              >取消</button>
              <button
                type="submit"
                class="btn-primary"
                :disabled="profileStore.saving"
              >{{ profileStore.saving ? '保存中...' : '保存' }}</button>
            </div>
          </form>
        </div>
      </section>

      <section class="card">
        <header class="card-header simple">
          <h2 class="card-title">设置 / 系统信息</h2>
        </header>
        <ul class="kv-list">
          <li>
            <span class="kv-label">服务地址</span>
            <span class="kv-value mono">{{ apiBase }}</span>
          </li>
          <li>
            <span class="kv-label">客户端版本</span>
            <span class="kv-value mono">{{ clientVersion }}</span>
          </li>
          <li>
            <span class="kv-label">当前用户 ID</span>
            <span class="kv-value mono">{{ user.id ?? '-' }}</span>
          </li>
        </ul>
        <div class="card-footer">
          <button type="button" class="btn-danger" @click="handleLogout">退出登录</button>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.profile-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card {
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-divider);
  overflow: hidden;
}

.card-header {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 20px;
  border-bottom: 1px solid var(--color-divider);
}
.card-header.simple {
  padding: 14px 20px;
}
.card-title {
  margin: 0;
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-secondary);
}

.avatar-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}
.avatar-edit {
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  border-radius: var(--radius-sm);
  padding: 4px 10px;
  font-size: 12px;
  color: var(--color-text-secondary);
  cursor: pointer;
  min-width: 80px;
}
.avatar-edit:hover:not(:disabled) {
  border-color: var(--color-primary);
  color: var(--color-primary);
}
.avatar-edit:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}
.hidden-input {
  display: none;
}

.meta {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}
.name {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--color-text-primary);
  word-break: break-word;
}
.caption {
  margin: 0;
  font-size: 13px;
  color: var(--color-text-secondary);
  word-break: break-word;
}

.card-body {
  padding: 16px 20px;
}
.bio-label {
  margin: 0 0 6px 0;
  font-size: 12px;
  color: var(--color-text-hint);
}
.bio-text {
  margin: 0;
  font-size: 14px;
  color: var(--color-text-primary);
  white-space: pre-wrap;
  word-break: break-word;
}

.edit-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.field-label {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.field-input {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 8px 10px;
  font-family: inherit;
  font-size: 14px;
  background: var(--color-surface);
  color: var(--color-text-primary);
  outline: none;
  resize: vertical;
}
.field-input:focus {
  border-color: var(--color-primary);
}
.form-error {
  margin: 0;
  font-size: 12px;
  color: var(--color-danger);
}
.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.btn-primary {
  padding: 6px 14px;
  border: none;
  border-radius: var(--radius-md);
  background: var(--color-primary);
  color: #ffffff;
  font-size: 13px;
  cursor: pointer;
}
.btn-primary:hover:not(:disabled) {
  background: var(--color-primary-dark);
}
.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.btn-secondary {
  padding: 6px 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  color: var(--color-text-secondary);
  font-size: 13px;
  cursor: pointer;
  align-self: flex-start;
}
.btn-secondary:hover:not(:disabled) {
  border-color: var(--color-primary);
  color: var(--color-primary);
}
.btn-secondary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.btn-danger {
  padding: 6px 14px;
  border: 1px solid var(--color-danger);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  color: var(--color-danger);
  font-size: 13px;
  cursor: pointer;
}
.btn-danger:hover {
  background: var(--color-danger);
  color: #ffffff;
}

.kv-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.kv-list li {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 20px;
  border-bottom: 1px solid var(--color-divider);
  gap: 16px;
}
.kv-list li:last-child {
  border-bottom: none;
}
.kv-label {
  font-size: 13px;
  color: var(--color-text-secondary);
}
.kv-value {
  font-size: 13px;
  color: var(--color-text-primary);
  text-align: right;
  word-break: break-all;
}
.kv-value.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, monospace;
  font-size: 12px;
}

.card-footer {
  padding: 12px 20px 16px 20px;
  border-top: 1px solid var(--color-divider);
  display: flex;
  justify-content: flex-end;
}
</style>

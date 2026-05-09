<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useProfileStore } from '../stores/profile'
import { useContactsStore } from '../stores/contacts'
import { useFlashNotesStore } from '../stores/flashNotes'
import { useFavoritesStore } from '../stores/favorites'
import { useToast } from '../composables/useToast'
import { uploadFile } from '../api/files'
import { countMessages } from '../api/messages'
import { buildAvatarUrl } from '../utils/avatarHelpers'
import LoadingState from '../components/LoadingState.vue'
import ErrorState from '../components/ErrorState.vue'
import AuthenticatedAvatar from '../components/AuthenticatedAvatar.vue'
import AvatarPickerDialog from '../components/AvatarPickerDialog.vue'
import AvatarCropDialog from '../components/AvatarCropDialog.vue'

// D1-W11 个人资料与设置
// - W11-01 进页拉一次 POST /api/users/profile
// - W11-02 编辑昵称/简介，PUT /api/users/profile
// - W11-03 上传头像：file → uploadFile 拿 objectName → 包成 download URL → PUT /api/users/avatar
// - W11-04 设置区：展示 API 基址、客户端版本、登出，不展示任何 token

const router = useRouter()
const authStore = useAuthStore()
const profileStore = useProfileStore()
const contactsStore = useContactsStore()
const flashNotesStore = useFlashNotesStore()
const favoritesStore = useFavoritesStore()
const { showSuccess, showError } = useToast()

const avatarUploading = ref(false)
const avatarProgress = ref(0)

// D1-W23-01 / W23-02 头像选择 + 裁剪
const pickerDialog = ref({ open: false })
const cropDialog = ref({ open: false, source: null })

const editing = ref(false)
const editForm = ref({ nickname: '', bio: '' })
const editError = ref('')

// D1-W16-02 消息总数（当前用户参与的全部消息数；接口失败保持静默不影响主流程）
const messageCount = ref(null)
async function loadMessageCount() {
  try {
    const value = await countMessages()
    const n = Number(value)
    messageCount.value = Number.isFinite(n) && n >= 0 ? n : null
  } catch (_e) {
    // 静默失败，UI 显示 '-'
    messageCount.value = null
  }
}

const NICK_MAX = 32
const BIO_MAX = 200
const AVATAR_MAX_BYTES = 5 * 1024 * 1024 // 5 MB

const user = computed(() => authStore.user || {})
// profile / apiBase / clientVersion 已迁到独立 /settings 页
const fallbackChar = computed(() => (profileStore.nickname || user.value.username || '?').slice(0, 1).toUpperCase())

const isLoadingFirst = computed(() => profileStore.loading && !profileStore.loaded)
const showError_ = computed(() => Boolean(profileStore.error) && !profileStore.loaded)

// D1-W23-05 统计扩充：闪记数 / 消息总数 / 收藏数
// 闪记数→ flashNotesStore.visibleCount（不含收集箱、不含隐藏）
// 收藏数 → favoritesStore.list.length（用户所有收藏的消息）
// 文件数 后端未提供独立 count 接口，暂不显示
const flashNoteCount = computed(() => flashNotesStore.loaded ? flashNotesStore.visibleCount : null)
const favoriteCount = computed(() => favoritesStore.loaded ? favoritesStore.list.length : null)

onMounted(async () => {
  if (!profileStore.loaded) {
    try {
      await profileStore.fetch()
    } catch (_e) {
      // store.error 已设
    }
  }
  // D1-W16-02 消息总数静默拉取，与 profile 加载并行
  loadMessageCount()
  // D1-W23-05 闪记与收藏统计静默拉取
  if (!flashNotesStore.loaded) flashNotesStore.fetchList({ silent: true }).catch(() => {})
  if (!favoritesStore.loaded) favoritesStore.fetchList().catch(() => {})
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

// D1-W23-01 打开头像选择对话框
function pickAvatar() {
  pickerDialog.value = { open: true }
}

// D1-W23-01 在 picker 里选中 emoji → 直接写入
async function onPickEmoji(emoji) {
  if (!emoji) return
  try {
    await profileStore.saveAvatar(emoji)
    authStore.patchUser({ avatar: emoji })
    showSuccess('头像已更新')
    pickerDialog.value = { open: false }
  } catch (err) {
    showError(err?.serverMessage || err?.message || '头像更新失败')
  }
}

// D1-W23-02 在 picker 里选择本地图片 → 弹裁剪对话框
function onPickImage(file) {
  if (!file) return
  if (!file.type || !file.type.startsWith('image/')) {
    showError('请选择图片文件')
    return
  }
  if (file.size > AVATAR_MAX_BYTES) {
    showError(`图片大小不能超过 ${AVATAR_MAX_BYTES / 1024 / 1024} MB`)
    return
  }
  pickerDialog.value = { open: false }
  cropDialog.value = { open: true, source: file }
}

// D1-W23-02 裁剪确认 → 上传 blob + PUT /api/users/avatar
async function onCropConfirm(blob) {
  if (!blob) return
  avatarUploading.value = true
  avatarProgress.value = 0
  try {
    // 转化成带文件名的 File 再上传，便于后端通过 contentType 判断
    const fileName = `avatar-${Date.now()}.jpg`
    const file = new File([blob], fileName, { type: 'image/jpeg' })
    const result = await uploadFile(file, {
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
    cropDialog.value = { open: false, source: null }
  }
}

function onCropCancel() {
  cropDialog.value = { open: false, source: null }
  // 取消裁剪后重新打开 picker，避免需要再点一次「更换头像」
  pickerDialog.value = { open: true }
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

      <!-- D1-W23-05 统计卡片 -->
      <section class="card stats-card">
        <header class="card-header simple">
          <h2 class="card-title">统计</h2>
        </header>
        <div class="stats-grid">
          <div class="stat-item">
            <span class="stat-value">{{ flashNoteCount == null ? '-' : flashNoteCount }}</span>
            <span class="stat-label">闪记</span>
          </div>
          <div class="stat-item">
            <span class="stat-value">{{ messageCount == null ? '-' : messageCount }}</span>
            <span class="stat-label">消息</span>
          </div>
          <div class="stat-item">
            <span class="stat-value">{{ favoriteCount == null ? '-' : favoriteCount }}</span>
            <span class="stat-label">收藏</span>
          </div>
        </div>
      </section>

      <section class="card">
        <header class="card-header simple">
          <h2 class="card-title">入口</h2>
        </header>
        <ul class="kv-list">
          <li class="link-row" @click="$router.push('/contacts')">
            <span class="kv-label">联系人 / 好友请求</span>
            <span class="kv-value">
              <span v-if="contactsStore.pendingCount > 0" class="badge">{{ contactsStore.pendingCount }}</span>
              <span class="chevron">›</span>
            </span>
          </li>
          <li class="link-row" @click="$router.push('/change-password')">
            <span class="kv-label">修改密码</span>
            <span class="kv-value">
              <span class="chevron">›</span>
            </span>
          </li>
          <li class="link-row" @click="$router.push({ name: 'settings' })">
            <span class="kv-label">⚙ 设置</span>
            <span class="kv-value">
              <span class="chevron">›</span>
            </span>
          </li>
        </ul>
      </section>

      <!-- 系统信息与退出已迁到独立设置页（/settings）；这里保留一个轻量的退出入口。 -->
      <section class="card">
        <div class="card-footer">
          <button type="button" class="btn-danger" @click="handleLogout">退出登录</button>
        </div>
      </section>
    </template>

    <!-- D1-W23-01 头像选择对话框 -->
    <AvatarPickerDialog
      v-model:open="pickerDialog.open"
      :busy="avatarUploading || profileStore.saving"
      :current-avatar="profileStore.avatar || ''"
      @select-emoji="onPickEmoji"
      @pick-image="onPickImage"
    />

    <!-- D1-W23-02 头像裁剪对话框 -->
    <AvatarCropDialog
      v-model:open="cropDialog.open"
      :source="cropDialog.source"
      @confirm="onCropConfirm"
      @cancel="onCropCancel"
    />
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
/* D1-W23-05 统计卡片网格 */
.stats-card .card-header {
  border-bottom: 1px solid var(--color-divider);
}
.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 0;
}
.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 14px 12px;
  border-right: 1px solid var(--color-divider);
}
.stat-item:last-child {
  border-right: none;
}
.stat-value {
  font-size: 22px;
  font-weight: 600;
  color: var(--color-text-primary);
}
.stat-label {
  font-size: 12px;
  color: var(--color-text-secondary);
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
.link-row {
  cursor: pointer;
}
.link-row:hover {
  background: var(--color-bg);
}
.chevron {
  font-size: 18px;
  color: var(--color-text-hint);
  margin-left: 8px;
}
.badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: var(--color-danger);
  color: #ffffff;
  font-size: 11px;
  font-weight: 600;
  margin-right: 4px;
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

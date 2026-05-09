<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useChatStore } from '../stores/chat'
import { useFlashNotesStore } from '../stores/flashNotes'
import { useFavoritesStore } from '../stores/favorites'
import { useAuthStore } from '../stores/auth'
import { useToast } from '../composables/useToast'
import { isOwnMessage, isInboxFlashNoteId } from '../utils/messageHelpers'
import LoadingState from '../components/LoadingState.vue'
import ErrorState from '../components/ErrorState.vue'
import EmptyState from '../components/EmptyState.vue'
import MessageBubble from '../components/MessageBubble.vue'
import MessageComposer from '../components/MessageComposer.vue'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import { uploadFile } from '../api/files'
import { inferMediaType } from '../utils/fileHelpers'
import { useChatScroll } from '../composables/useChatScroll'

// D1-W6 单条会话页（独立顶级路由 /chat/:flashNoteId）
// - 进入时根据 :flashNoteId 拉首页（page=1, limit=30）
// - 滚动到顶部触发 loadMore
// - 发送：optimistic + serverMessage 替换；失败保留输入并标红消息
// - 删除：单条 + 多选两种入口，都走 ConfirmDialog 二次确认

const route = useRoute()
const router = useRouter()
const chatStore = useChatStore()
const flashNotesStore = useFlashNotesStore()
const favoritesStore = useFavoritesStore()
const authStore = useAuthStore()
const { showSuccess, showError } = useToast()

const composerRef = ref(null)
const uploadProgress = ref(0)
const uploading = ref(false)

const flashNoteId = computed(() => Number(route.params.flashNoteId))

// D1-W19 滚动行为统一：scrollerRef / scrollToBottom / handleScroll / 新消息提示
// / sessionStorage 位置记忆等都封装在 useChatScroll 里。
const {
  scrollerRef,
  hasNewBelow,
  scrollToBottom,
  handleScroll,
  rememberScroll,
  restoreScrollOrBottom
} = useChatScroll({
  flashNoteId,
  messages: computed(() => chatStore.messages),
  shouldLoadMore: () => chatStore.hasMore && !chatStore.loadingMore,
  onLoadMore: () => chatStore.loadMore()
})

const headerTitle = computed(() => {
  if (isInboxFlashNoteId(flashNoteId.value)) {
    return '收集箱'
  }
  // 优先从 flashNotes store 读 title；store 没数据时回退一个占位标题
  const note = flashNotesStore.list.find((n) => n && Number(n.id) === flashNoteId.value)
  return note?.title || '闪记会话'
})

const headerIcon = computed(() => {
  if (isInboxFlashNoteId(flashNoteId.value)) return '📥'
  const note = flashNotesStore.list.find((n) => n && Number(n.id) === flashNoteId.value)
  return note?.icon || '⚡'
})

const currentUserId = computed(() => authStore.user?.id ?? null)

const initialLoading = computed(() => chatStore.loading && chatStore.messages.length === 0)
const showError_ = computed(() => Boolean(chatStore.error) && chatStore.messages.length === 0)
const showEmpty = computed(
  () => !chatStore.loading && !chatStore.error && chatStore.messages.length === 0
)

// 删除确认
const deleteDialog = ref({ open: false, mode: 'single', target: null, busy: false })

// D1-W16-01 收集箱清空确认
const clearInboxDialog = ref({ open: false, busy: false })

// D1-W17-01 合并为卡片对话框
const mergeDialog = ref({ open: false, busy: false, title: '' })

// D1-W17-02 卡片详情对话框（只读，后端无 update 接口）
const cardDetailDialog = ref({ open: false, message: null })

// D1-W17-03 转发对话框
const forwardDialog = ref({ open: false, busy: false, targetFlashNoteId: null })

const forwardableNotes = computed(() => {
  // 排除当前会话；优先列出非 inbox + 非 hidden + 非 deleted 的闪记
  return (flashNotesStore.list || []).filter(
    (n) => n
      && !n.deleted
      && !n.hidden
      && Number(n.id) !== flashNoteId.value
  )
})

const isInbox = computed(() => isInboxFlashNoteId(flashNoteId.value))

function askClearInbox() {
  clearInboxDialog.value = { open: true, busy: false }
}

function cancelClearInbox() {
  if (clearInboxDialog.value.busy) return
  clearInboxDialog.value = { open: false, busy: false }
}

async function confirmClearInbox() {
  clearInboxDialog.value.busy = true
  try {
    await chatStore.clearInbox()
    clearInboxDialog.value = { open: false, busy: false }
    showSuccess('收集箱已清空')
    // 同步刷新闪记列表，让收集箱项的 latestMessage 也更新
    flashNotesStore.fetchList({ silent: true }).catch(() => {})
  } catch (e) {
    clearInboxDialog.value.busy = false
    showError(e?.serverMessage || e?.message || '清空失败')
  }
}

async function reload() {
  if (flashNoteId.value == null || Number.isNaN(flashNoteId.value)) {
    router.replace('/notes')
    return
  }
  try {
    await chatStore.openConversation(flashNoteId.value)
    // W19-02 进入会话优先恢复 sessionStorage 上次位置，没有再滚到底
    await restoreScrollOrBottom()
  } catch (_e) {
    // 错误展示由 store.error 驱动
  }
}

onMounted(() => {
  // 列表 store 没加载时顺手拉一下，便于头部显示真实 title/icon
  if (!flashNotesStore.loaded) {
    flashNotesStore.fetchList({ silent: true }).catch(() => {})
  }
  // 收藏集 silent 拉一次，便于消息气泡显示当前收藏状态
  if (!favoritesStore.loaded) {
    favoritesStore.fetchList({ silent: true }).catch(() => {})
  }
  reload()
})

onBeforeUnmount(() => {
  // W19-02 离开会话前记下当前 scrollTop（按 flashNoteId 隔离）
  rememberScroll()
  chatStore.reset()
})

watch(
  () => route.params.flashNoteId,
  (next, prev) => {
    if (route.name !== 'chat') return
    // 切换到下一个会话前先把当前位置写入 sessionStorage，再 reload
    if (prev != null && String(prev) !== String(next)) {
      rememberScroll()
    }
    reload()
  }
)

// MessageComposer @submit 的 payload 是 { text, file }
//   - text：字符串文本（可能为空）
//   - file：浏览器 File 对象（可能为 null）
// 这里需要：先把 file 通过 /api/files/upload 拿到 objectName，再把 media 信息传给 chatStore.send。
// 历史 bug：之前直接 `handleSend(content)` 把整个 payload 对象当作字符串塞进 store，
// 触发 `(content || '').trim is not a function`。
async function handleSend(payload) {
  const text = (payload && typeof payload.text === 'string') ? payload.text : ''
  const file = payload && payload.file ? payload.file : null

  let media = null
  if (file) {
    uploading.value = true
    uploadProgress.value = 0
    try {
      const result = await uploadFile(file, {
        onUploadProgress: (e) => {
          if (e && e.total) {
            uploadProgress.value = e.loaded / e.total
          }
        }
      })
      const objectName = result?.objectName
      if (!objectName) {
        throw new Error('上传失败：缺少 objectName')
      }
      media = {
        mediaType: inferMediaType(file),
        mediaUrl: objectName,
        fileName: result?.originalFilename || file.name,
        fileSize: file.size != null ? Number(file.size) : null
      }
    } catch (e) {
      uploading.value = false
      uploadProgress.value = 0
      showError(e?.serverMessage || e?.message || '附件上传失败')
      return
    }
    uploading.value = false
    uploadProgress.value = 0
  }

  try {
    await chatStore.send({
      content: text,
      currentUserId: currentUserId.value,
      media
    })
    composerRef.value?.reset()
    await nextTick()
    // W19-03 发送后强制平滑滚到底部（用户主动操作的反馈）
    scrollToBottom({ smooth: true })
  } catch (e) {
    // W6-06：失败保留输入，仅 toast
    showError(e?.serverMessage || e?.message || '发送失败')
  }
}

async function handleRetry(clientRequestId) {
  try {
    await chatStore.retryFailed(clientRequestId, currentUserId.value)
  } catch (e) {
    showError(e?.serverMessage || e?.message || '重试失败')
  }
}

async function handleToggleFavorite(message) {
  if (!message || message.id == null) return
  const isFav = favoritesStore.isFavorited(message.id)
  try {
    if (isFav) {
      await favoritesStore.remove(message.id)
      showSuccess('已取消收藏')
    } else {
      await favoritesStore.add(message.id)
      showSuccess('已收藏')
    }
  } catch (e) {
    showError(e?.serverMessage || e?.message || '操作失败')
  }
}

function askDeleteSingle(message) {
  deleteDialog.value = { open: true, mode: 'single', target: message, busy: false }
}

function askDeleteBatch() {
  if (chatStore.selectedIds.size === 0) {
    showError('请选择消息')
    return
  }
  deleteDialog.value = { open: true, mode: 'batch', target: null, busy: false }
}

function cancelDelete() {
  deleteDialog.value = { open: false, mode: 'single', target: null, busy: false }
}

async function confirmDelete() {
  deleteDialog.value.busy = true
  try {
    if (deleteDialog.value.mode === 'batch') {
      await chatStore.batchRemove()
      showSuccess('已删除')
    } else if (deleteDialog.value.target) {
      await chatStore.remove(deleteDialog.value.target.id)
      showSuccess('已删除')
    }
    deleteDialog.value = { open: false, mode: 'single', target: null, busy: false }
  } catch (e) {
    deleteDialog.value.busy = false
    showError(e?.serverMessage || e?.message || '删除失败')
  }
}

function goBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.replace('/notes')
  }
}

function toggleSelectMode() {
  if (chatStore.selectMode) {
    chatStore.exitSelectMode()
  } else {
    chatStore.enterSelectMode()
  }
}

// D1-W17-01 合并为卡片
function askMerge() {
  if (chatStore.selectedIds.size === 0) {
    showError('请先选择消息')
    return
  }
  if (chatStore.selectedIds.size > 50) {
    showError('单次最多合并 50 条')
    return
  }
  mergeDialog.value = { open: true, busy: false, title: '' }
}
function cancelMerge() {
  if (mergeDialog.value.busy) return
  mergeDialog.value = { open: false, busy: false, title: '' }
}
async function confirmMerge() {
  const title = mergeDialog.value.title.trim()
  if (!title) {
    showError('请输入卡片标题')
    return
  }
  mergeDialog.value.busy = true
  try {
    await chatStore.mergeSelected({ title })
    mergeDialog.value = { open: false, busy: false, title: '' }
    showSuccess('已合并为卡片')
    await nextTick()
    // W19-03 合并卡片成功后跟随到底
    scrollToBottom({ smooth: true })
  } catch (e) {
    mergeDialog.value.busy = false
    showError(e?.serverMessage || e?.message || '合并失败')
  }
}

// D1-W17-02 卡片详情
function openCardDetail(message) {
  if (!message) return
  cardDetailDialog.value = { open: true, message }
}
function closeCardDetail() {
  cardDetailDialog.value = { open: false, message: null }
}

// D1-W17-03 转发
function askForward() {
  if (chatStore.selectedIds.size === 0) {
    showError('请先选择消息')
    return
  }
  if (!flashNotesStore.loaded) {
    flashNotesStore.fetchList({ silent: true }).catch(() => {})
  }
  forwardDialog.value = { open: true, busy: false, targetFlashNoteId: null }
}
function cancelForward() {
  if (forwardDialog.value.busy) return
  forwardDialog.value = { open: false, busy: false, targetFlashNoteId: null }
}
async function confirmForward() {
  const targetId = forwardDialog.value.targetFlashNoteId
  if (targetId == null) {
    showError('请选择目标闪记')
    return
  }
  forwardDialog.value.busy = true
  try {
    const { successCount, failures } = await chatStore.forwardSelected({
      targetFlashNoteId: targetId,
      currentUserId: currentUserId.value
    })
    forwardDialog.value = { open: false, busy: false, targetFlashNoteId: null }
    if (failures.length === 0) {
      showSuccess(`已转发 ${successCount} 条`)
    } else if (successCount === 0) {
      showError(`转发失败：${failures[0].error}`)
    } else {
      showError(`部分转发失败（成功 ${successCount} / 失败 ${failures.length}）`)
    }
  } catch (e) {
    forwardDialog.value.busy = false
    showError(e?.serverMessage || e?.message || '转发失败')
  }
}
</script>

<template>
  <div class="chat-page">
    <header class="chat-header">
      <button type="button" class="header-back" @click="goBack" aria-label="返回">←</button>
      <div class="header-title">
        <span class="header-icon" aria-hidden="true">{{ headerIcon }}</span>
        <span class="header-text">{{ headerTitle }}</span>
      </div>
      <div class="header-actions">
        <button type="button" class="header-btn" @click="toggleSelectMode">
          {{ chatStore.selectMode ? '取消多选' : '多选' }}
        </button>
        <template v-if="chatStore.selectMode">
          <button
            type="button"
            class="header-btn"
            :disabled="chatStore.selectedIds.size === 0"
            @click="askMerge"
            title="合并所选消息为卡片"
          >合并</button>
          <button
            type="button"
            class="header-btn"
            :disabled="chatStore.selectedIds.size === 0"
            @click="askForward"
            title="转发所选消息到其他闪记"
          >转发</button>
          <button
            type="button"
            class="header-btn danger"
            :disabled="chatStore.selectedIds.size === 0"
            @click="askDeleteBatch"
          >删除（{{ chatStore.selectedIds.size }}）</button>
        </template>
        <button
          v-if="isInbox && !chatStore.selectMode"
          type="button"
          class="header-btn danger"
          :disabled="chatStore.messages.length === 0"
          @click="askClearInbox"
          title="清空收集箱内全部消息"
        >清空</button>
      </div>
    </header>

    <main
      class="chat-scroll"
      ref="scrollerRef"
      @scroll="handleScroll"
    >
      <LoadingState v-if="initialLoading" text="加载消息中..." />
      <ErrorState
        v-else-if="showError_"
        :message="chatStore.error"
        @retry="reload"
      />
      <template v-else>
        <div v-if="chatStore.loadingMore" class="load-more-tip">加载历史中...</div>
        <EmptyState
          v-if="showEmpty"
          icon="💬"
          title="还没有消息"
          description="在下方输入并按 Enter 发送第一条消息"
        />
        <MessageBubble
          v-for="m in chatStore.messages"
          :key="m.id != null ? `id:${m.id}` : `cr:${m.clientRequestId}`"
          :message="m"
          :mine="isOwnMessage(m, currentUserId)"
          :select-mode="chatStore.selectMode"
          :selected="m.id != null && chatStore.selectedIds.has(m.id)"
          :favorited="m.id != null && favoritesStore.isFavorited(m.id)"
          @toggle-select="(id) => chatStore.toggleSelect(id)"
          @delete="askDeleteSingle"
          @retry="handleRetry"
          @toggle-favorite="handleToggleFavorite"
          @open-card="openCardDetail"
        />
      </template>

      <!-- D1-W19-01 回到底部悬浮按钮：仅当用户滚出底部时显示；
           hasNewBelow 表示有新消息到达 → 圆点提示 -->
      <button
        v-if="hasNewBelow"
        type="button"
        class="jump-to-bottom"
        :title="'有新消息，点击回到底部'"
        @click="scrollToBottom({ smooth: true })"
      >
        <span class="jump-arrow" aria-hidden="true">⬇</span>
        <span class="jump-text">新消息</span>
      </button>
    </main>

    <MessageComposer
      ref="composerRef"
      :busy="chatStore.sending || uploading"
      :upload-progress="uploadProgress"
      @submit="handleSend"
    />

    <ConfirmDialog
      :open="deleteDialog.open"
      :busy="deleteDialog.busy"
      title="删除消息"
      :message="deleteDialog.mode === 'batch'
        ? `确认删除选中的 ${chatStore.selectedIds.size} 条消息吗？此操作不可撤销。`
        : '确认删除这条消息吗？此操作不可撤销。'"
      confirm-label="删除"
      danger
      @confirm="confirmDelete"
      @cancel="cancelDelete"
    />

    <ConfirmDialog
      :open="clearInboxDialog.open"
      :busy="clearInboxDialog.busy"
      title="清空收集箱"
      message="确认清空收集箱内的全部消息？此操作不可撤销。"
      confirm-label="清空"
      danger
      @confirm="confirmClearInbox"
      @cancel="cancelClearInbox"
    />

    <!-- D1-W17-01 合并为卡片：标题输入弹窗 -->
    <div v-if="mergeDialog.open" class="modal-overlay" @click.self="cancelMerge">
      <div class="modal" role="dialog" aria-label="合并为卡片">
        <header class="modal-header">
          <h2 class="modal-title">合并为卡片</h2>
          <button type="button" class="modal-close" @click="cancelMerge">×</button>
        </header>
        <div class="modal-body">
          <p class="modal-desc">
            将所选 {{ chatStore.selectedIds.size }} 条消息合并为一张卡片消息（原消息不会被删除）。
          </p>
          <input
            v-model="mergeDialog.title"
            type="text"
            class="modal-input"
            placeholder="请输入卡片标题"
            maxlength="64"
            :disabled="mergeDialog.busy"
            @keydown.enter.prevent="confirmMerge"
          />
        </div>
        <footer class="modal-footer">
          <button type="button" class="btn-secondary" :disabled="mergeDialog.busy" @click="cancelMerge">取消</button>
          <button
            type="button"
            class="btn-primary"
            :disabled="mergeDialog.busy || !mergeDialog.title.trim()"
            @click="confirmMerge"
          >{{ mergeDialog.busy ? '合并中...' : '合并' }}</button>
        </footer>
      </div>
    </div>

    <!-- D1-W17-02 卡片详情：只读 -->
    <div v-if="cardDetailDialog.open" class="modal-overlay" @click.self="closeCardDetail">
      <div class="modal modal-large" role="dialog" aria-label="卡片详情">
        <header class="modal-header">
          <h2 class="modal-title">{{ cardDetailDialog.message?.payload?.title || '卡片消息' }}</h2>
          <button type="button" class="modal-close" @click="closeCardDetail">×</button>
        </header>
        <div class="modal-body card-detail">
          <p v-if="cardDetailDialog.message?.payload?.summary" class="card-detail-summary">
            {{ cardDetailDialog.message.payload.summary }}
          </p>
          <ul class="card-items">
            <li
              v-for="(item, i) in (cardDetailDialog.message?.payload?.items || [])"
              :key="i"
              class="card-item"
            >
              <span class="card-item-index">{{ i + 1 }}</span>
              <div class="card-item-body">
                <p class="card-item-meta">{{ item.type || 'TEXT' }} · {{ item.role || '-' }}</p>
                <p v-if="item.content" class="card-item-content">{{ item.content }}</p>
                <p v-if="item.fileName" class="card-item-file">📎 {{ item.fileName }}</p>
              </div>
            </li>
          </ul>
          <p class="card-detail-hint">提示：当前后端不支持卡片编辑；如需修改，请重新合并新卡片。</p>
        </div>
      </div>
    </div>

    <!-- D1-W17-03 转发：选择目标闪记 -->
    <div v-if="forwardDialog.open" class="modal-overlay" @click.self="cancelForward">
      <div class="modal" role="dialog" aria-label="转发到">
        <header class="modal-header">
          <h2 class="modal-title">转发到</h2>
          <button type="button" class="modal-close" @click="cancelForward">×</button>
        </header>
        <div class="modal-body">
          <p class="modal-desc">将所选 {{ chatStore.selectedIds.size }} 条消息转发到其他闪记。</p>
          <ul v-if="forwardableNotes.length" class="forward-list">
            <li
              v-for="n in forwardableNotes"
              :key="n.id"
              class="forward-item"
              :class="{ active: forwardDialog.targetFlashNoteId === n.id }"
              @click="forwardDialog.targetFlashNoteId = n.id"
            >
              <span class="forward-icon">{{ n.icon || '⚡' }}</span>
              <span class="forward-title">{{ n.title || '未命名闪记' }}</span>
            </li>
          </ul>
          <p v-else class="empty-text">没有可转发的目标闪记</p>
        </div>
        <footer class="modal-footer">
          <button type="button" class="btn-secondary" :disabled="forwardDialog.busy" @click="cancelForward">取消</button>
          <button
            type="button"
            class="btn-primary"
            :disabled="forwardDialog.busy || forwardDialog.targetFlashNoteId == null"
            @click="confirmForward"
          >{{ forwardDialog.busy ? '转发中...' : '转发' }}</button>
        </footer>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--color-bg);
}

/* D1-W17 modal 样式（合并/卡片详情/转发共用） */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 50;
  padding: 16px;
}
.modal {
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  width: min(420px, 100%);
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  box-shadow: var(--shadow-md);
  overflow: hidden;
}
.modal-large { width: min(520px, 100%); }
.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--color-divider);
}
.modal-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}
.modal-close {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  border: none;
  background: var(--color-divider);
  color: var(--color-text-secondary);
  cursor: pointer;
  font-size: 16px;
  line-height: 1;
}
.modal-body {
  padding: 16px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 10px 16px;
  border-top: 1px solid var(--color-divider);
}
.modal-desc {
  margin: 0;
  font-size: 13px;
  color: var(--color-text-secondary);
}
.modal-input {
  padding: 8px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  font-size: 14px;
  background: var(--color-surface);
  color: var(--color-text-primary);
  outline: none;
}
.modal-input:focus { border-color: var(--color-primary); }

.btn-primary {
  padding: 6px 14px;
  border: none;
  border-radius: var(--radius-md);
  background: var(--color-primary);
  color: #ffffff;
  font-size: 13px;
  cursor: pointer;
}
.btn-primary:disabled { opacity: 0.55; cursor: not-allowed; }
.btn-secondary {
  padding: 6px 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  color: var(--color-text-secondary);
  font-size: 13px;
  cursor: pointer;
}
.btn-secondary:disabled { opacity: 0.55; cursor: not-allowed; }

.card-detail-summary {
  margin: 0;
  padding: 8px 12px;
  background: var(--color-bg);
  border-radius: var(--radius-md);
  font-size: 13px;
  color: var(--color-text-secondary);
}
.card-items {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.card-item {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 10px;
  padding: 10px;
  border: 1px solid var(--color-divider);
  border-radius: var(--radius-md);
}
.card-item-index {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--color-primary-light);
  color: var(--color-primary);
  text-align: center;
  font-size: 12px;
  font-weight: 600;
  line-height: 22px;
}
.card-item-body { min-width: 0; }
.card-item-meta {
  margin: 0 0 4px 0;
  font-size: 11px;
  color: var(--color-text-hint);
  text-transform: uppercase;
}
.card-item-content {
  margin: 0;
  font-size: 13px;
  color: var(--color-text-primary);
  white-space: pre-wrap;
  word-break: break-word;
}
.card-item-file {
  margin: 4px 0 0 0;
  font-size: 12px;
  color: var(--color-text-secondary);
}
.card-detail-hint {
  margin: 8px 0 0 0;
  font-size: 12px;
  color: var(--color-text-hint);
  text-align: center;
}

.forward-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 280px;
  overflow-y: auto;
}
.forward-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border: 1px solid var(--color-divider);
  border-radius: var(--radius-md);
  cursor: pointer;
}
.forward-item:hover { border-color: var(--color-primary); }
.forward-item.active {
  border-color: var(--color-primary);
  background: var(--color-primary-light);
}
.forward-icon {
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--color-bg);
  border-radius: 50%;
  font-size: 14px;
}
.forward-title {
  font-size: 14px;
  color: var(--color-text-primary);
  word-break: break-word;
}
.empty-text {
  margin: 16px 0;
  text-align: center;
  color: var(--color-text-hint);
  font-size: 13px;
}

.chat-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: var(--color-topbar-bg);
  border-bottom: 1px solid var(--color-divider);
  flex-shrink: 0;
}
.header-back {
  width: 32px;
  height: 32px;
  border: 1px solid var(--color-border);
  border-radius: 50%;
  background: var(--color-surface);
  cursor: pointer;
  font-size: 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.header-back:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}
.header-title {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.header-icon {
  font-size: 18px;
}
.header-text {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.header-actions {
  display: flex;
  gap: 6px;
}
.header-btn {
  padding: 6px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  font-size: 13px;
  color: var(--color-text-secondary);
  cursor: pointer;
}
.header-btn:hover:not(:disabled) {
  border-color: var(--color-primary);
  color: var(--color-primary);
}
.header-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.header-btn.danger:not(:disabled) {
  border-color: var(--color-danger);
  color: var(--color-danger);
}

.chat-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  position: relative; /* 给 jump-to-bottom 提供定位上下文 */
}

/* D1-W19-01 回到底部悬浮按钮：sticky + bottom，跟随容器滚动而非整页固定 */
.jump-to-bottom {
  position: sticky;
  bottom: 8px;
  align-self: center;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border-radius: 999px;
  border: none;
  background: var(--color-primary);
  color: #ffffff;
  font-size: 13px;
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.18);
  /* 让按钮浮在最新消息上方一点点；负 margin 避免占用 chat 列表本身的空间 */
  margin-top: -36px;
  margin-bottom: 4px;
  z-index: 5;
  transition: transform 0.15s, background 0.15s;
}
.jump-to-bottom:hover {
  background: var(--color-primary-dark);
  transform: translateY(-1px);
}
.jump-arrow {
  font-size: 14px;
  line-height: 1;
}
.jump-text {
  font-size: 12px;
}
.load-more-tip {
  text-align: center;
  font-size: 12px;
  color: var(--color-text-hint);
  padding: 8px 0;
}

/* W12-01 桌面超宽屏聊天体验：消息区两侧 padding 自动扩大，
   把消息列宽视觉收窄到 var(--chat-max-width) 居中，
   避免在 4K 屏上一行气泡跨度过大；header / composer 保持全宽以贴合页面框架。
   max() 兜底，确保窄屏时不会出现负 padding。 */
@media (min-width: 1280px) {
  .chat-scroll {
    padding-left: max(16px, calc((100% - var(--chat-max-width)) / 2));
    padding-right: max(16px, calc((100% - var(--chat-max-width)) / 2));
  }
}

/* W12-02 移动端 ≤480px 收紧聊天 header 与 padding */
@media (max-width: 480px) {
  .chat-scroll {
    padding: 12px;
  }
}
</style>

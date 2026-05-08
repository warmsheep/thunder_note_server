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
const scrollerRef = ref(null)

const flashNoteId = computed(() => Number(route.params.flashNoteId))

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

async function reload() {
  if (flashNoteId.value == null || Number.isNaN(flashNoteId.value)) {
    router.replace('/notes')
    return
  }
  try {
    await chatStore.openConversation(flashNoteId.value)
    await nextTick()
    scrollToBottom()
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
  chatStore.reset()
})

watch(
  () => route.params.flashNoteId,
  () => {
    if (route.name === 'chat') {
      reload()
    }
  }
)

function scrollToBottom() {
  const el = scrollerRef.value
  if (!el) return
  el.scrollTop = el.scrollHeight
}

async function handleScroll() {
  const el = scrollerRef.value
  if (!el) return
  // 接近顶部触发加载历史，记录当前 scrollHeight 以便保持视觉位置
  if (el.scrollTop < 80 && chatStore.hasMore && !chatStore.loadingMore) {
    const beforeHeight = el.scrollHeight
    try {
      await chatStore.loadMore()
      await nextTick()
      const after = scrollerRef.value
      if (after) {
        after.scrollTop = after.scrollHeight - beforeHeight
      }
    } catch (_e) {
      // 错误已经被 store 捕获
    }
  }
}

async function handleSend(content) {
  try {
    await chatStore.send({ content, currentUserId: currentUserId.value })
    composerRef.value?.clear()
    await nextTick()
    scrollToBottom()
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
        <button
          v-if="chatStore.selectMode"
          type="button"
          class="header-btn danger"
          :disabled="chatStore.selectedIds.size === 0"
          @click="askDeleteBatch"
        >删除（{{ chatStore.selectedIds.size }}）</button>
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
        />
      </template>
    </main>

    <MessageComposer
      ref="composerRef"
      :busy="chatStore.sending"
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
  </div>
</template>

<style scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--color-bg);
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
}
.load-more-tip {
  text-align: center;
  font-size: 12px;
  color: var(--color-text-hint);
  padding: 8px 0;
}
</style>

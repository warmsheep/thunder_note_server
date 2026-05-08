<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useFavoritesStore } from '../stores/favorites'
import { useToast } from '../composables/useToast'
import LoadingState from '../components/LoadingState.vue'
import ErrorState from '../components/ErrorState.vue'
import EmptyState from '../components/EmptyState.vue'

// D1-W8 收藏列表
// - 拉 favorites/list 展示已收藏消息（W8-01）
// - 每项可"取消收藏"（W8-03）
// - 点击项跳到 /chat/:flashNoteId（W8-04）；flashNoteId 缺失时给提示

const router = useRouter()
const store = useFavoritesStore()
const { showSuccess, showError } = useToast()

const removingId = ref(null)

onMounted(() => {
  if (!store.loaded) {
    store.fetchList().catch(() => {})
  }
})

const initialLoading = computed(() => store.loading && !store.loaded)
const showError_ = computed(() => Boolean(store.error) && !store.loaded)
const showEmpty = computed(() => store.loaded && store.list.length === 0)

function handleRetry() {
  store.fetchList()
}

async function handleRemove(item) {
  if (!item || item.messageId == null) return
  removingId.value = item.messageId
  try {
    await store.remove(item.messageId)
    showSuccess('已取消收藏')
  } catch (e) {
    showError(e?.serverMessage || e?.message || '取消收藏失败')
  } finally {
    removingId.value = null
  }
}

function openOrigin(item) {
  if (!item) return
  if (item.flashNoteId == null) {
    showError('原闪记已不存在')
    return
  }
  router.push({ name: 'chat', params: { flashNoteId: String(item.flashNoteId) } })
}

function previewOf(item) {
  if (!item) return ''
  if (item.payload && item.payload.cardType) {
    return `[卡片] ${item.payload.title || item.payload.cardType}`
  }
  if (item.mediaType) {
    return `[${item.mediaType}]${item.fileName ? ' ' + item.fileName : ''}`
  }
  return item.content || ''
}

function timeText(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  const M = d.getMonth() + 1
  const D = d.getDate()
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  return `${M}/${D} ${hh}:${mm}`
}
</script>

<template>
  <div class="favorites-page">
    <div class="page-toolbar">
      <span class="page-stats">共 {{ store.list.length }} 条收藏</span>
    </div>

    <LoadingState v-if="initialLoading" text="加载收藏中..." />
    <ErrorState
      v-else-if="showError_"
      :message="store.error"
      @retry="handleRetry"
    />
    <template v-else>
      <EmptyState
        v-if="showEmpty"
        icon="⭐"
        title="还没有收藏"
        description="在闪记会话中点消息上的“☆ 收藏”按钮即可加入这里"
      />
      <section v-else class="group">
        <article
          v-for="item in store.sortedList"
          :key="item.id"
          class="fav-item"
          @click="openOrigin(item)"
        >
          <div class="fav-flashnote">
            <span class="fn-icon" aria-hidden="true">{{ item.flashNoteIcon || '⚡' }}</span>
            <span class="fn-title">{{ item.flashNoteTitle || '未知闪记' }}</span>
          </div>
          <p class="fav-content">{{ previewOf(item) }}</p>
          <div class="fav-meta">
            <span class="fav-time">{{ timeText(item.favoritedAt) }} 收藏</span>
            <button
              type="button"
              class="action danger"
              :disabled="removingId === item.messageId"
              @click.stop="handleRemove(item)"
            >{{ removingId === item.messageId ? '处理中...' : '取消收藏' }}</button>
          </div>
        </article>
      </section>
    </template>
  </div>
</template>

<style scoped>
.favorites-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.page-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.page-stats {
  font-size: 13px;
  color: var(--color-text-secondary);
}

.group {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.fav-item {
  background: var(--color-surface);
  border: 1px solid var(--color-divider);
  border-radius: var(--radius-lg);
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  cursor: pointer;
  transition: border-color 0.12s, background 0.12s;
}
.fav-item:hover {
  border-color: var(--color-primary);
  background: var(--color-bg);
}

.fav-flashnote {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--color-text-secondary);
}
.fn-icon {
  font-size: 16px;
}
.fn-title {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.fav-content {
  margin: 0;
  font-size: 14px;
  color: var(--color-text-primary);
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 5em;
  overflow: hidden;
}
.fav-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: var(--color-text-hint);
}
.action {
  padding: 4px 10px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  font-size: 12px;
  color: var(--color-text-secondary);
  cursor: pointer;
}
.action:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.action.danger:hover:not(:disabled) {
  border-color: var(--color-danger);
  color: var(--color-danger);
}
</style>

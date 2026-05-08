<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useSearchStore } from '../stores/search'
import { useToast } from '../composables/useToast'
import LoadingState from '../components/LoadingState.vue'
import ErrorState from '../components/ErrorState.vue'
import EmptyState from '../components/EmptyState.vue'

// D1-W10 全文搜索
// - 输入框 + 搜索按钮（Enter 键也触发）
// - 结果分两组展示：闪记名称命中、消息内容命中
// - 点击闪记名称 → /chat/:id
// - 点击消息片段 → /chat/:id（先打开会话，定位到具体消息留待后续）
// - runId 守卫由 store 侧处理，UI 层只观察 loading/error/results

const router = useRouter()
const store = useSearchStore()
const { showError } = useToast()

const inputEl = ref(null)
const inputValue = ref(store.query || '')

onMounted(() => {
  // 进入页面焦点直接放在输入框，便于立刻搜索
  setTimeout(() => inputEl.value?.focus(), 0)
})

async function doSearch() {
  store.setQuery(inputValue.value)
  try {
    await store.search(inputValue.value)
  } catch (e) {
    // store 已写 error，再 toast 也可
    showError(e?.serverMessage || e?.message || '搜索失败')
  }
}

function handleEnter(e) {
  if (e.shiftKey) return
  e.preventDefault()
  doSearch()
}

function clearInput() {
  inputValue.value = ''
  store.clear()
  inputEl.value?.focus()
}

function openConversation(flashNoteId) {
  if (flashNoteId == null) {
    showError('原闪记已不存在')
    return
  }
  router.push({ name: 'chat', params: { flashNoteId: String(flashNoteId) } })
}

function shortenSnippet(text, max = 80) {
  if (!text) return ''
  const s = String(text).replace(/\s+/g, ' ').trim()
  if (s.length <= max) return s
  return s.slice(0, max - 1) + '…'
}

const showInitialHint = computed(() => !store.hasSearched && !store.loading && !store.error)
</script>

<template>
  <div class="search-page">
    <div class="search-bar">
      <input
        ref="inputEl"
        v-model="inputValue"
        type="text"
        class="search-input"
        placeholder="搜索闪记名称或消息内容"
        @keydown.enter="handleEnter"
      />
      <button
        v-if="inputValue"
        type="button"
        class="search-clear"
        title="清空"
        @click="clearInput"
      >×</button>
      <button
        type="button"
        class="search-btn"
        :disabled="store.loading || !inputValue.trim()"
        @click="doSearch"
      >{{ store.loading ? '搜索中...' : '搜索' }}</button>
    </div>

    <p v-if="store.activeQuery && !store.loading && !store.error" class="search-summary">
      关键词「{{ store.activeQuery }}」 · 共 {{ store.totalHits }} 条结果
    </p>

    <LoadingState v-if="store.loading" text="搜索中..." />
    <ErrorState
      v-else-if="store.error"
      :message="store.error"
      @retry="doSearch"
    />
    <template v-else>
      <EmptyState
        v-if="showInitialHint"
        icon="🔍"
        title="搜索闪记和消息"
        description="输入关键词后按 Enter 或点搜索按钮"
      />
      <EmptyState
        v-else-if="store.isEmptyAfterSearch"
        icon="🤔"
        title="没有找到结果"
        :description="`未匹配到名称或内容包含「${store.activeQuery}」的闪记/消息`"
      />
      <template v-else>
        <section v-if="store.noteHits.length" class="group">
          <header class="group-header">闪记名称命中（{{ store.noteHits.length }}）</header>
          <article
            v-for="(item, idx) in store.noteHits"
            :key="`name-${item.flashNote?.id ?? idx}`"
            class="hit-row"
            @click="openConversation(item.flashNote?.id)"
          >
            <span class="hit-icon" aria-hidden="true">{{ item.flashNote?.icon || '⚡' }}</span>
            <div class="hit-meta">
              <p class="hit-title">{{ item.flashNote?.title || '(未命名闪记)' }}</p>
              <p class="hit-sub">{{ item.flashNote?.latestMessage || '点击进入会话' }}</p>
            </div>
          </article>
        </section>

        <section v-if="store.messageHits.length" class="group">
          <header class="group-header">消息内容命中（{{ store.messageHits.length }}）</header>
          <article
            v-for="(item, idx) in store.messageHits"
            :key="`msg-${item.flashNote?.id ?? idx}`"
            class="hit-row"
            @click="openConversation(item.flashNote?.id)"
          >
            <span class="hit-icon" aria-hidden="true">{{ item.flashNote?.icon || '⚡' }}</span>
            <div class="hit-meta">
              <p class="hit-title">{{ item.flashNote?.title || '(未命名闪记)' }}</p>
              <ul v-if="item.matchedMessages && item.matchedMessages.length" class="hit-snippets">
                <li
                  v-for="(snip, sIdx) in item.matchedMessages"
                  :key="`sn-${snip.messageId ?? sIdx}`"
                  class="hit-snippet"
                >{{ shortenSnippet(snip.snippet) || '[空消息]' }}</li>
              </ul>
            </div>
          </article>
        </section>
      </template>
    </template>
  </div>
</template>

<style scoped>
.search-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.search-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 4px 6px;
}
.search-input {
  flex: 1;
  border: none;
  background: transparent;
  font-size: 14px;
  outline: none;
  padding: 8px 8px;
  color: var(--color-text-primary);
}
.search-clear {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  border: none;
  background: var(--color-divider);
  color: var(--color-text-secondary);
  cursor: pointer;
  font-size: 14px;
  line-height: 1;
}
.search-clear:hover {
  background: var(--color-text-secondary);
  color: var(--color-surface);
}
.search-btn {
  padding: 6px 14px;
  border: none;
  border-radius: var(--radius-md);
  background: var(--color-primary);
  color: #ffffff;
  font-size: 14px;
  cursor: pointer;
}
.search-btn:hover:not(:disabled) {
  background: var(--color-primary-dark);
}
.search-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.search-summary {
  margin: 0;
  font-size: 13px;
  color: var(--color-text-secondary);
}

.group {
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-divider);
  overflow: hidden;
}
.group-header {
  padding: 10px 16px;
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-secondary);
  background: var(--color-bg);
  border-bottom: 1px solid var(--color-divider);
}

.hit-row {
  display: grid;
  grid-template-columns: 32px 1fr;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--color-divider);
  cursor: pointer;
  transition: background 0.12s;
}
.hit-row:last-child {
  border-bottom: none;
}
.hit-row:hover {
  background: var(--color-bg);
}
.hit-icon {
  font-size: 22px;
  text-align: center;
}
.hit-meta {
  min-width: 0;
}
.hit-title {
  margin: 0;
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.hit-sub {
  margin: 2px 0 0 0;
  font-size: 12px;
  color: var(--color-text-hint);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.hit-snippets {
  margin: 6px 0 0 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.hit-snippet {
  font-size: 13px;
  color: var(--color-text-primary);
  padding: 4px 8px;
  background: var(--color-bg);
  border-radius: var(--radius-sm);
  word-break: break-word;
}
</style>

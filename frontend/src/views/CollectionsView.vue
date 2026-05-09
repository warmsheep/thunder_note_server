<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useCollectionsStore } from '../stores/collections'
import { useFlashNotesStore } from '../stores/flashNotes'
import { useToast } from '../composables/useToast'
import { groupNotesByCollection, listNotesInCollection } from '../utils/collectionHelpers'
import LoadingState from '../components/LoadingState.vue'
import ErrorState from '../components/ErrorState.vue'
import EmptyState from '../components/EmptyState.vue'
import CollectionEditDialog from '../components/CollectionEditDialog.vue'
import ConfirmDialog from '../components/ConfirmDialog.vue'

// D1-W7 合集目录
// - 合集 CRUD（W7-01/03/04/05）
// - 合集 - 闪记层级（W7-02）：基于 flashNote.tags === collection.name 的软关联
// - 列表项可展开查看其下闪记，点击闪记跳到 /chat/:id

const router = useRouter()
const collectionsStore = useCollectionsStore()
const flashNotesStore = useFlashNotesStore()
const { showSuccess, showError } = useToast()

const editDialog = ref({ open: false, mode: 'create', initial: {}, target: null })
const deleteDialog = ref({ open: false, target: null, busy: false })
// D1-W28-10 默认全部展开：语义反转为「折叠集」。初始空集合 = 全部展开，
// 点击一项才会加入折叠集。未分类同样默认展开。
const collapsed = ref(new Set()) // 折叠状态下的 collection id
const uncatCollapsed = ref(false)
function isExpanded(id) {
  return !collapsed.value.has(id)
}

onMounted(() => {
  if (!collectionsStore.loaded) {
    collectionsStore.fetchList().catch(() => {})
  }
  if (!flashNotesStore.loaded) {
    flashNotesStore.fetchList({ silent: true }).catch(() => {})
  }
})

const initialLoading = computed(() => collectionsStore.loading && !collectionsStore.loaded)
const showError_ = computed(() => Boolean(collectionsStore.error) && !collectionsStore.loaded)

const grouped = computed(() =>
  groupNotesByCollection(flashNotesStore.list, collectionsStore.list)
)

const showEmpty = computed(
  () => collectionsStore.loaded && collectionsStore.list.length === 0 && grouped.value.uncategorized.length === 0
)

function handleRetry() {
  collectionsStore.fetchList()
}

function openCreate() {
  editDialog.value = { open: true, mode: 'create', initial: {}, target: null }
}

function openEdit(c) {
  editDialog.value = {
    open: true,
    mode: 'edit',
    initial: { name: c.name, description: c.description },
    target: c
  }
}

function closeEdit() {
  editDialog.value = { open: false, mode: 'create', initial: {}, target: null }
}

async function handleEditSubmit(payload) {
  try {
    if (editDialog.value.mode === 'edit' && editDialog.value.target) {
      await collectionsStore.update(editDialog.value.target.id, payload)
      showSuccess('已保存')
      // 改名会让后端 cascadeRename 把 flashNote.tags 同步更新，本地 flashNotes 列表需要刷新一次
      flashNotesStore.fetchList({ silent: true }).catch(() => {})
    } else {
      await collectionsStore.create(payload)
      showSuccess('已创建')
    }
    closeEdit()
  } catch (e) {
    showError(e?.serverMessage || e?.message || '操作失败')
  }
}

function askDelete(c) {
  deleteDialog.value = { open: true, target: c, busy: false }
}

async function confirmDelete() {
  if (!deleteDialog.value.target) return
  deleteDialog.value.busy = true
  try {
    await collectionsStore.remove(deleteDialog.value.target.id)
    showSuccess('已删除')
    // 后端 cascadeClear 会把对应闪记的 tags 置 null，刷新闪记列表保持一致
    flashNotesStore.fetchList({ silent: true }).catch(() => {})
    deleteDialog.value = { open: false, target: null, busy: false }
  } catch (e) {
    deleteDialog.value.busy = false
    showError(e?.serverMessage || e?.message || '删除失败')
  }
}

function cancelDelete() {
  deleteDialog.value = { open: false, target: null, busy: false }
}

function toggleExpand(id) {
  const next = new Set(collapsed.value)
  if (next.has(id)) {
    next.delete(id)
  } else {
    next.add(id)
  }
  collapsed.value = next
}

function notesOfCollection(c) {
  return listNotesInCollection(flashNotesStore.list, c.name)
}

function openChat(note) {
  if (!note || note.id == null) return
  router.push({ name: 'chat', params: { flashNoteId: String(note.id) } })
}
</script>

<template>
  <div class="collections-page">
    <div class="page-toolbar">
      <span class="page-stats">
        共 {{ collectionsStore.list.length }} 个合集
        <span v-if="grouped.uncategorized.length"> · 未分类 {{ grouped.uncategorized.length }} 条闪记</span>
      </span>
      <button type="button" class="btn-create" :disabled="collectionsStore.submitting" @click="openCreate">
        + 新建合集
      </button>
    </div>

    <LoadingState v-if="initialLoading" text="加载合集中..." />
    <ErrorState
      v-else-if="showError_"
      :message="collectionsStore.error"
      @retry="handleRetry"
    />
    <template v-else>
      <EmptyState
        v-if="showEmpty"
        icon="📂"
        title="还没有合集"
        description="点右上角“新建合集”开始整理"
      />
      <template v-else>
        <section v-if="collectionsStore.sortedList.length" class="group">
          <header class="group-header">合集</header>
          <article
            v-for="c in collectionsStore.sortedList"
            :key="c.id"
            class="collection-item"
          >
            <div class="collection-row" @click="toggleExpand(c.id)">
              <span class="collection-icon" aria-hidden="true">📂</span>
              <div class="collection-meta">
                <p class="collection-name">{{ c.name }}</p>
                <p v-if="c.description" class="collection-desc">{{ c.description }}</p>
              </div>
              <span class="collection-count">{{ notesOfCollection(c).length }} 条</span>
              <span class="caret" aria-hidden="true">{{ isExpanded(c.id) ? '▾' : '▸' }}</span>
              <div class="collection-actions">
                <button type="button" class="action" @click.stop="openEdit(c)">编辑</button>
                <button type="button" class="action danger" @click.stop="askDelete(c)">删除</button>
              </div>
            </div>
            <div v-if="isExpanded(c.id)" class="collection-notes">
              <p v-if="notesOfCollection(c).length === 0" class="empty-line">该合集下还没有闪记</p>
              <button
                v-for="n in notesOfCollection(c)"
                :key="n.id"
                type="button"
                class="note-line"
                @click="openChat(n)"
              >
                <span class="note-icon">{{ n.icon || '⚡' }}</span>
                <span class="note-title">{{ n.title }}</span>
                <span class="note-preview">{{ n.latestMessage || '暂无消息' }}</span>
              </button>
            </div>
          </article>
        </section>

        <section v-if="grouped.uncategorized.length" class="group">
          <header class="group-header collapsible" @click="uncatCollapsed = !uncatCollapsed">
            <span>未分类（{{ grouped.uncategorized.length }}）</span>
            <span class="caret">{{ uncatCollapsed ? '▸' : '▾' }}</span>
          </header>
          <div v-if="!uncatCollapsed" class="collection-notes flat">
            <button
              v-for="n in grouped.uncategorized"
              :key="n.id"
              type="button"
              class="note-line"
              @click="openChat(n)"
            >
              <span class="note-icon">{{ n.icon || '⚡' }}</span>
              <span class="note-title">{{ n.title }}</span>
              <span class="note-preview">{{ n.latestMessage || '暂无消息' }}</span>
            </button>
          </div>
        </section>
      </template>
    </template>

    <CollectionEditDialog
      :open="editDialog.open"
      :mode="editDialog.mode"
      :initial="editDialog.initial"
      :busy="collectionsStore.submitting"
      @submit="handleEditSubmit"
      @cancel="closeEdit"
    />
    <ConfirmDialog
      :open="deleteDialog.open"
      :busy="deleteDialog.busy"
      title="删除合集"
      :message="deleteDialog.target
        ? `确认删除合集“${deleteDialog.target.name}”吗？该合集下的闪记会变成“未分类”，闪记本身不会被删除。`
        : ''"
      confirm-label="删除"
      danger
      @confirm="confirmDelete"
      @cancel="cancelDelete"
    />
  </div>
</template>

<style scoped>
.collections-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.page-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.page-stats {
  font-size: 13px;
  color: var(--color-text-secondary);
}
.btn-create {
  padding: 8px 16px;
  border: none;
  border-radius: var(--radius-md);
  background: var(--color-primary);
  color: #ffffff;
  font-size: 14px;
  cursor: pointer;
}
.btn-create:hover:not(:disabled) {
  background: var(--color-primary-dark);
}
.btn-create:disabled {
  opacity: 0.6;
  cursor: not-allowed;
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
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.group-header.collapsible {
  cursor: pointer;
  user-select: none;
}

.collection-item {
  border-bottom: 1px solid var(--color-divider);
}
.collection-item:last-child {
  border-bottom: none;
}
.collection-row {
  display: grid;
  grid-template-columns: 32px 1fr auto auto auto;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  cursor: pointer;
  transition: background 0.12s;
}
.collection-row:hover {
  background: var(--color-bg);
}
.collection-icon {
  font-size: 22px;
  text-align: center;
}
.collection-meta {
  min-width: 0;
}
.collection-name {
  margin: 0;
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.collection-desc {
  margin: 2px 0 0 0;
  font-size: 12px;
  color: var(--color-text-hint);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.collection-count {
  font-size: 12px;
  color: var(--color-text-secondary);
  white-space: nowrap;
}
.caret {
  font-size: 12px;
  color: var(--color-text-hint);
}
.collection-actions {
  display: flex;
  gap: 4px;
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
.action:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}
.action.danger:hover {
  border-color: var(--color-danger);
  color: var(--color-danger);
}

.collection-notes {
  padding: 4px 12px 12px 60px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  background: var(--color-bg);
}
.collection-notes.flat {
  padding: 8px 16px 12px 16px;
}
.empty-line {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-hint);
  padding: 6px 0;
}
.note-line {
  display: grid;
  grid-template-columns: 24px auto 1fr;
  gap: 8px;
  align-items: center;
  padding: 6px 8px;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  text-align: left;
  cursor: pointer;
  font-size: 13px;
  color: var(--color-text-primary);
}
.note-line:hover {
  background: var(--color-surface);
}
.note-line .note-icon {
  font-size: 16px;
  text-align: center;
}
.note-line .note-title {
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.note-line .note-preview {
  font-size: 12px;
  color: var(--color-text-hint);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

@media (max-width: 768px) {
  .collection-actions .action {
    padding: 4px 8px;
    font-size: 11px;
  }
  .collection-notes {
    padding-left: 32px;
  }
}
</style>

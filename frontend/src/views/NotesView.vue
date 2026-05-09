<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useFlashNotesStore } from '../stores/flashNotes'
import { useChatStore } from '../stores/chat'
import { useCollectionsStore } from '../stores/collections'
import { useAuthStore } from '../stores/auth'
import { useSearchStore } from '../stores/search'
import { useToast } from '../composables/useToast'
import { useSwipeReveal } from '../composables/useSwipeReveal'
import { uploadFile } from '../api/files'
import { inferMediaType } from '../utils/fileHelpers'
import LoadingState from '../components/LoadingState.vue'
import ErrorState from '../components/ErrorState.vue'
import EmptyState from '../components/EmptyState.vue'
import NoteEditDialog from '../components/NoteEditDialog.vue'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import QuickCaptureDialog from '../components/QuickCaptureDialog.vue'
import MessageActionMenu from '../components/MessageActionMenu.vue'
import CardEditorDialog from '../components/CardEditorDialog.vue'

// D1-W22-01 闪记主页 FAB（快速捕获菜单）
// - 右下角悬浮「+」按钮，点击弹出「文字 / 图片 / 视频 / 文件 / 拍照」菜单
// - 任一选项 → 直接发到收集箱（flashNoteId=-1），不进会话页
// - 拍照仅移动端可见

// D1-W5 闪记列表页
// - 复用 EmptyState/LoadingState/ErrorState 三态
// - 收集箱（inbox=true）固定置顶展示，不可编辑/删除/隐藏
// - 普通闪记按 pinned/normal/hidden 三段展示，hidden 折叠
// - 列表项菜单：编辑、置顶/取消置顶、隐藏/取消隐藏、删除（带二次确认）
// - D1-W28-02 NoteEditDialog 已接入合集 chip：编辑/创建闪记时可单选合集（tags 字段）

const store = useFlashNotesStore()
const chatStore = useChatStore()
const collectionsStore = useCollectionsStore()
const authStore = useAuthStore()
const searchStore = useSearchStore()
const { showSuccess, showError } = useToast()
const router = useRouter()

const INBOX_FLASH_NOTE_ID = -1

const currentUserId = computed(() => authStore.user?.id ?? null)
const isMobileLayout = ref(false)
const { begin, move, end, close, offsetOf, isOpen } = useSwipeReveal()
const inlineSearchOpen = ref(false)
const inlineSearchInput = ref('')
const actionMenuOpen = ref(false)
const actionMenuX = ref(0)
const actionMenuY = ref(0)
const actionMenuTarget = ref(null)
const LONG_PRESS_MS = 600
const LONG_PRESS_TOLERANCE_PX = 8
let longPressTimer = null
let pressStart = { x: 0, y: 0 }

function updateLayout() {
  if (typeof window === 'undefined' || !window.matchMedia) {
    isMobileLayout.value = false
    return
  }
  isMobileLayout.value = window.matchMedia('(pointer: coarse)').matches
}
updateLayout()
let mqList = null
if (typeof window !== 'undefined' && window.matchMedia) {
  mqList = window.matchMedia('(pointer: coarse)')
  if (typeof mqList.addEventListener === 'function') {
    mqList.addEventListener('change', updateLayout)
  }
}
onBeforeUnmount(() => {
  if (longPressTimer) {
    clearTimeout(longPressTimer)
    longPressTimer = null
  }
  if (mqList && typeof mqList.removeEventListener === 'function') {
    mqList.removeEventListener('change', updateLayout)
  }
})

function openChat(note) {
  if (!note || note.id == null) return
  router.push({ name: 'chat', params: { flashNoteId: String(note.id) } })
}

const editDialog = ref({ open: false, mode: 'create', initial: {}, target: null })
const deleteDialog = ref({ open: false, target: null, busy: false })
const showHidden = ref(false)
const noteActionMenuItems = computed(() => {
  const note = actionMenuTarget.value
  if (!note) return []
  return [
    { key: 'edit', label: '编辑', icon: '✏️' },
    { key: 'pin', label: note.pinned ? '取消置顶' : '置顶', icon: note.pinned ? '📍' : '📌' },
    { key: 'hide', label: note.hidden ? '取消隐藏' : '隐藏', icon: '🙈' },
    { key: 'delete', label: '删除', icon: '🗑', danger: true }
  ]
})
const showingSearchResults = computed(() => inlineSearchOpen.value && searchStore.hasSearched)

onMounted(() => {
  if (!store.loaded) {
    store.fetchList().catch(() => {
      // 错误状态由 store.error 驱动 ErrorState
    })
  }
  // D1-W28-02 NoteEditDialog 需要合集列表来源；与闪记列表并行拉取，失败静默
  if (!collectionsStore.loaded) {
    collectionsStore.fetchList({ silent: true }).catch(() => {})
  }
})

const isInitialLoading = computed(() => store.loading && !store.loaded)
const showError_ = computed(() => Boolean(store.error) && !store.loaded)
const showEmpty = computed(() =>
  store.loaded && !store.inboxNote && store.pinnedList.length === 0 && store.normalList.length === 0
)

function handleRetry() {
  store.fetchList()
}

function openCreate() {
  editDialog.value = { open: true, mode: 'create', initial: {}, target: null }
}

function openEdit(note) {
  editDialog.value = {
    open: true,
    mode: 'edit',
    // D1-W28-02 把当前 tags（合集名）一并塞进 initial，让 NoteEditDialog 高亮当前合集
    initial: { title: note.title, icon: note.icon, tags: note.tags || '' },
    target: note
  }
}

function closeEdit() {
  editDialog.value = { open: false, mode: 'create', initial: {}, target: null }
}

async function handleEditSubmit(payload) {
  try {
    if (editDialog.value.mode === 'edit' && editDialog.value.target) {
      await store.update(editDialog.value.target.id, payload)
      showSuccess('已保存')
    } else {
      await store.create(payload)
      showSuccess('已创建')
    }
    closeEdit()
  } catch (e) {
    showError(e?.serverMessage || e?.message || '操作失败')
  }
}

async function togglePin(note) {
  try {
    await store.setPinned(note.id, !note.pinned)
    showSuccess(note.pinned ? '已取消置顶' : '已置顶')
  } catch (e) {
    showError(e?.serverMessage || e?.message || '操作失败')
  }
}

async function toggleHide(note) {
  try {
    await store.setHidden(note.id, !note.hidden)
    showSuccess(note.hidden ? '已取消隐藏' : '已隐藏')
  } catch (e) {
    showError(e?.serverMessage || e?.message || '操作失败')
  }
}

function askDelete(note) {
  close(note?.id)
  deleteDialog.value = { open: true, target: note, busy: false }
}

async function confirmDelete() {
  if (!deleteDialog.value.target) return
  deleteDialog.value.busy = true
  try {
    await store.remove(deleteDialog.value.target.id)
    showSuccess('已删除')
    deleteDialog.value = { open: false, target: null, busy: false }
  } catch (e) {
    deleteDialog.value.busy = false
    showError(e?.serverMessage || e?.message || '删除失败')
  }
}

function cancelDelete() {
  deleteDialog.value = { open: false, target: null, busy: false }
}

function openInlineSearch() {
  inlineSearchOpen.value = true
}

function closeInlineSearch() {
  inlineSearchOpen.value = false
  inlineSearchInput.value = ''
  searchStore.clear()
}

async function runInlineSearch() {
  searchStore.setQuery(inlineSearchInput.value)
  await searchStore.search(inlineSearchInput.value)
}

function clearLongPress() {
  if (longPressTimer) {
    clearTimeout(longPressTimer)
    longPressTimer = null
  }
}

function openActionMenu(note, x, y) {
  actionMenuTarget.value = note
  actionMenuX.value = Math.round(x)
  actionMenuY.value = Math.round(y)
  actionMenuOpen.value = true
}

function handleNoteContextMenu(event, note) {
  event.preventDefault()
  openActionMenu(note, event.clientX, event.clientY)
}

function onNoteTouchStart(event, note) {
  if (!isMobileLayout.value || !note?.id) return
  begin(note.id, event, event.currentTarget?.offsetWidth || 240)
  const touch = event.touches?.[0]
  if (!touch) return
  pressStart = { x: touch.clientX, y: touch.clientY }
  clearLongPress()
  longPressTimer = setTimeout(() => {
    longPressTimer = null
    openActionMenu(note, pressStart.x, pressStart.y)
  }, LONG_PRESS_MS)
}

function onNoteTouchMove(event, note) {
  if (!isMobileLayout.value || !note?.id) return
  move(note.id, event)
  if (!longPressTimer) return
  const touch = event.touches?.[0]
  if (!touch) return
  const dx = Math.abs(touch.clientX - pressStart.x)
  const dy = Math.abs(touch.clientY - pressStart.y)
  if (dx > LONG_PRESS_TOLERANCE_PX || dy > LONG_PRESS_TOLERANCE_PX) {
    clearLongPress()
  }
}

function onNoteTouchEnd(note) {
  clearLongPress()
  if (!isMobileLayout.value || !note?.id) return
  end(note.id)
}

async function onNoteActionSelect(key) {
  const note = actionMenuTarget.value
  if (!note) return
  if (key === 'edit') {
    openEdit(note)
  } else if (key === 'pin') {
    await togglePin(note)
  } else if (key === 'hide') {
    await toggleHide(note)
  } else if (key === 'delete') {
    askDelete(note)
  }
}

function openSearchConversation(item) {
  const flashNoteId = item?.flashNote?.id
  if (flashNoteId == null) return
  openChat({ id: flashNoteId })
}

function formatTime(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  const now = new Date()
  const sameDay = d.toDateString() === now.toDateString()
  if (sameDay) {
    const hh = String(d.getHours()).padStart(2, '0')
    const mm = String(d.getMinutes()).padStart(2, '0')
    return `${hh}:${mm}`
  }
  const M = d.getMonth() + 1
  const D = d.getDate()
  return `${M}/${D}`
}

// ---- W22-01 快速捕获 FAB 菜单 ----
const fabMenuOpen = ref(false)
const fabMenuX = ref(0)
const fabMenuY = ref(0)
const fabBtnEl = ref(null)

const fabMenuItems = computed(() => {
  const items = [
    { key: 'text', label: '文字', icon: '📝' },
    { key: 'image', label: '图片', icon: '🖼' },
    { key: 'video', label: '视频', icon: '🎥' },
    { key: 'file', label: '文件', icon: '📎' }
  ]
  if (isMobileLayout.value) {
    items.push({ key: 'camera', label: '拍照', icon: '📷' })
  }
  // D1-W26-02 与 Android `PopupQuickCaptureActions` 对齐，
  // 收集箱也支持直接新建多媒体卡片（默认 flashNoteId=-1）
  items.push({ key: 'card', label: '卡片', icon: '📇' })
  return items
})

function openFabMenu() {
  // 在按钮上方弹出：计算 button 左上角位置（MessageActionMenu 反翻机制会自动处理边界）
  const btn = fabBtnEl.value
  if (btn && typeof btn.getBoundingClientRect === 'function') {
    const rect = btn.getBoundingClientRect()
    // 默认在按钮上方：菜单随后会反翻适适进视口
    fabMenuX.value = Math.round(rect.right - 200)
    fabMenuY.value = Math.round(rect.top - 8 - 36 * fabMenuItems.value.length)
  }
  fabMenuOpen.value = true
}

// W22-02 快记文本对话框
const quickDialog = ref({ open: false, busy: false })
const quickDialogRef = ref(null)

function openQuickText() {
  quickDialog.value = { open: true, busy: false }
}
async function handleQuickTextSubmit(text) {
  if (!text || !text.trim()) return
  quickDialog.value.busy = true
  try {
    // 走 store：不需要提前调 openConversation。send 接收 flashNoteId 参数？
    // 现有 chatStore.send 只是在「当前会话」的上下文发送，这里需要临时到收集箱会话。
    // 为了不污染 chatStore 全局状态，直接调 messages.api.sendMessage。
    const { sendMessage } = await import('../api/messages')
    await sendMessage({
      flashNoteId: INBOX_FLASH_NOTE_ID,
      content: String(text)
    })
    showSuccess('已发送到收集箱')
    quickDialog.value = { open: false, busy: false }
    quickDialogRef.value?.reset()
    // 刷新闪记列表以更新收集箱预览
    store.fetchList({ silent: true }).catch(() => {})
  } catch (e) {
    quickDialog.value.busy = false
    showError(e?.serverMessage || e?.message || '发送失败')
  }
}
function handleQuickTextCancel() {
  quickDialog.value = { open: false, busy: false }
}

// W22-01 隐藏的文件 input（重用为图片 / 视频 / 文件 / 拍照）
const hiddenFileInput = ref(null)
const hiddenFileAccept = ref('')
const hiddenFileCapture = ref('')
const pendingFileKind = ref(null)
const quickUploading = ref(false)

function triggerFilePicker(kind) {
  pendingFileKind.value = kind
  if (kind === 'image' || kind === 'camera') {
    hiddenFileAccept.value = 'image/*'
  } else if (kind === 'video') {
    hiddenFileAccept.value = 'video/*'
  } else {
    hiddenFileAccept.value = ''
  }
  hiddenFileCapture.value = kind === 'camera' ? 'environment' : ''
  // 下一帧手动 click，为让 accept/capture 绑定生效
  setTimeout(() => hiddenFileInput.value?.click(), 0)
}

async function onHiddenFileChange(e) {
  const file = e.target.files && e.target.files[0]
  if (e.target) e.target.value = ''
  if (!file) return
  quickUploading.value = true
  try {
    const result = await uploadFile(file)
    const objectName = result?.objectName
    if (!objectName) throw new Error('上传失败：缺少 objectName')
    const { sendMessage } = await import('../api/messages')
    await sendMessage({
      flashNoteId: INBOX_FLASH_NOTE_ID,
      content: '',
      mediaType: inferMediaType(file),
      mediaUrl: objectName,
      fileName: result?.originalFilename || file.name,
      fileSize: file.size != null ? Number(file.size) : null
    })
    showSuccess('已发送到收集箱')
    store.fetchList({ silent: true }).catch(() => {})
  } catch (err) {
    showError(err?.serverMessage || err?.message || '发送失败')
  } finally {
    quickUploading.value = false
  }
}

// D1-W26-02 卡片新建（目标固定为收集箱，flashNoteId=-1）
const cardEditor = ref({ open: false })

function openCardEditor() {
  cardEditor.value = { open: true }
}
function onCardEditorCreated() {
  showSuccess('已发送到收集箱')
  // 刷新闪记列表以更新收集箱预览
  store.fetchList({ silent: true }).catch(() => {})
}

function onFabMenuSelect(key) {
  if (key === 'text') {
    openQuickText()
  } else if (key === 'card') {
    openCardEditor()
  } else {
    triggerFilePicker(key)
  }
}
void chatStore
void currentUserId
</script>

<template>
  <div class="notes-page">
    <div class="page-toolbar">
      <span class="page-stats" v-if="store.loaded">共 {{ store.visibleCount }} 条</span>
      <span class="page-stats" v-else></span>
      <div class="toolbar-actions">
        <button
          type="button"
          class="btn-search-toggle"
          :class="{ active: inlineSearchOpen }"
          @click="inlineSearchOpen ? closeInlineSearch() : openInlineSearch()"
        >🔍</button>
        <button type="button" class="btn-create" :disabled="store.submitting" @click="openCreate">+ 新建闪记</button>
      </div>
    </div>

    <form v-if="inlineSearchOpen" class="inline-search" @submit.prevent="runInlineSearch">
      <input
        v-model="inlineSearchInput"
        type="search"
        class="inline-search-input"
        placeholder="搜索闪记名称或消息内容"
      />
      <button type="submit" class="inline-search-btn" :disabled="searchStore.loading || !inlineSearchInput.trim()">
        {{ searchStore.loading ? '搜索中...' : '搜索' }}
      </button>
      <button type="button" class="inline-search-close" @click="closeInlineSearch">关闭</button>
    </form>

    <LoadingState v-if="isInitialLoading" text="加载闪记中..." />
    <ErrorState
      v-else-if="showError_"
      :message="store.error"
      @retry="handleRetry"
    />
    <template v-else>
      <EmptyState
        v-if="showEmpty"
        icon="⚡"
        title="还没有闪记"
        description="点击右上角 + 创建第一个"
      />
      <template v-else-if="showingSearchResults">
        <section class="group">
          <header class="group-header">闪记名称命中（{{ searchStore.noteHits.length }}）</header>
          <article
            v-for="(item, idx) in searchStore.noteHits"
            :key="`search-note-${item.flashNote?.id ?? idx}`"
            class="search-hit"
            @click="openSearchConversation(item)"
          >
            <span class="note-icon" aria-hidden="true">{{ item.flashNote?.icon || '⚡' }}</span>
            <div class="note-meta">
              <p class="note-title">{{ item.flashNote?.title || '(未命名闪记)' }}</p>
              <p class="note-preview">{{ item.flashNote?.latestMessage || '点击进入会话' }}</p>
            </div>
          </article>
        </section>
        <section class="group" v-if="searchStore.messageHits.length">
          <header class="group-header">消息内容命中（{{ searchStore.messageHits.length }}）</header>
          <article
            v-for="(item, idx) in searchStore.messageHits"
            :key="`search-msg-${item.flashNote?.id ?? idx}`"
            class="search-hit"
            @click="openSearchConversation(item)"
          >
            <span class="note-icon" aria-hidden="true">{{ item.flashNote?.icon || '⚡' }}</span>
            <div class="note-meta">
              <p class="note-title">{{ item.flashNote?.title || '(未命名闪记)' }}</p>
              <p class="note-preview">{{ item.matchedMessages?.[0]?.snippet || '点击进入会话' }}</p>
            </div>
          </article>
        </section>
      </template>
      <template v-else>
        <section v-if="store.inboxNote" class="group">
          <header class="group-header">收集箱</header>
          <article class="note-item inbox clickable" @click="openChat({ id: -1 })">
            <div class="note-icon" aria-hidden="true">{{ store.inboxNote.icon || '📥' }}</div>
            <div class="note-meta">
              <p class="note-title">{{ store.inboxNote.title || '收集箱' }}</p>
              <p class="note-preview">{{ store.inboxNote.latestMessage || '默认收集入口' }}</p>
            </div>
            <span class="note-time">{{ formatTime(store.inboxNote.updatedAt) }}</span>
          </article>
        </section>

        <section v-if="store.pinnedList.length" class="group">
          <header class="group-header">置顶</header>
          <article
            v-for="note in store.pinnedList"
            :key="note.id"
            class="note-swipe-shell"
          >
            <button
              v-if="isMobileLayout"
              type="button"
              class="swipe-delete-action"
              :class="{ open: isOpen(note.id) }"
              @click="askDelete(note)"
            >删除</button>
            <div
              class="note-item clickable"
              :style="isMobileLayout ? { transform: `translateX(${offsetOf(note.id)}px)` } : undefined"
              @click="openChat(note)"
              @contextmenu="handleNoteContextMenu($event, note)"
              @touchstart.passive="onNoteTouchStart($event, note)"
              @touchmove.passive="onNoteTouchMove($event, note)"
              @touchend="onNoteTouchEnd(note)"
              @touchcancel="onNoteTouchEnd(note)"
            >
              <div class="note-icon" aria-hidden="true">{{ note.icon || '⚡' }}</div>
              <div class="note-meta">
                <p class="note-title">
                  <span class="badge-pinned" aria-hidden="true">📌</span>
                  {{ note.title }}
                </p>
                <p class="note-preview">{{ note.latestMessage || '暂无消息' }}</p>
              </div>
              <span class="note-time">{{ formatTime(note.updatedAt) }}</span>
              <div class="note-actions" v-if="!isMobileLayout">
                <button type="button" class="action" @click.stop="openEdit(note)">编辑</button>
                <button type="button" class="action" @click.stop="togglePin(note)">取消置顶</button>
                <button type="button" class="action" @click.stop="toggleHide(note)">隐藏</button>
                <button type="button" class="action danger" @click.stop="askDelete(note)">删除</button>
              </div>
            </div>
          </article>
        </section>

        <section v-if="store.normalList.length" class="group">
          <header class="group-header">最近</header>
          <article
            v-for="note in store.normalList"
            :key="note.id"
            class="note-swipe-shell"
          >
            <button
              v-if="isMobileLayout"
              type="button"
              class="swipe-delete-action"
              :class="{ open: isOpen(note.id) }"
              @click="askDelete(note)"
            >删除</button>
            <div
              class="note-item clickable"
              :style="isMobileLayout ? { transform: `translateX(${offsetOf(note.id)}px)` } : undefined"
              @click="openChat(note)"
              @contextmenu="handleNoteContextMenu($event, note)"
              @touchstart.passive="onNoteTouchStart($event, note)"
              @touchmove.passive="onNoteTouchMove($event, note)"
              @touchend="onNoteTouchEnd(note)"
              @touchcancel="onNoteTouchEnd(note)"
            >
            <div class="note-icon" aria-hidden="true">{{ note.icon || '⚡' }}</div>
            <div class="note-meta">
              <p class="note-title">{{ note.title }}</p>
              <p class="note-preview">{{ note.latestMessage || '暂无消息' }}</p>
            </div>
            <span class="note-time">{{ formatTime(note.updatedAt) }}</span>
            <div class="note-actions" v-if="!isMobileLayout">
              <button type="button" class="action" @click.stop="openEdit(note)">编辑</button>
              <button type="button" class="action" @click.stop="togglePin(note)">置顶</button>
              <button type="button" class="action" @click.stop="toggleHide(note)">隐藏</button>
              <button type="button" class="action danger" @click.stop="askDelete(note)">删除</button>
            </div>
            </div>
          </article>
        </section>

        <section v-if="store.hiddenList.length" class="group">
          <header class="group-header collapsible" @click="showHidden = !showHidden">
            <span>隐藏（{{ store.hiddenList.length }}）</span>
            <span class="caret">{{ showHidden ? '▾' : '▸' }}</span>
          </header>
          <template v-if="showHidden">
            <article
              v-for="note in store.hiddenList"
              :key="note.id"
              class="note-swipe-shell"
            >
              <button
                v-if="isMobileLayout"
                type="button"
                class="swipe-delete-action"
                :class="{ open: isOpen(note.id) }"
                @click="askDelete(note)"
              >删除</button>
              <div
                class="note-item dimmed clickable"
                :style="isMobileLayout ? { transform: `translateX(${offsetOf(note.id)}px)` } : undefined"
                @click="openChat(note)"
                @contextmenu="handleNoteContextMenu($event, note)"
                @touchstart.passive="onNoteTouchStart($event, note)"
                @touchmove.passive="onNoteTouchMove($event, note)"
                @touchend="onNoteTouchEnd(note)"
                @touchcancel="onNoteTouchEnd(note)"
              >
              <div class="note-icon" aria-hidden="true">{{ note.icon || '⚡' }}</div>
              <div class="note-meta">
                <p class="note-title">{{ note.title }}</p>
                <p class="note-preview">{{ note.latestMessage || '暂无消息' }}</p>
              </div>
              <span class="note-time">{{ formatTime(note.updatedAt) }}</span>
              <div class="note-actions" v-if="!isMobileLayout">
                <button type="button" class="action" @click.stop="openEdit(note)">编辑</button>
                <button type="button" class="action" @click.stop="toggleHide(note)">取消隐藏</button>
                <button type="button" class="action danger" @click.stop="askDelete(note)">删除</button>
              </div>
              </div>
            </article>
          </template>
        </section>
      </template>
    </template>

    <NoteEditDialog
      :open="editDialog.open"
      :mode="editDialog.mode"
      :initial="editDialog.initial"
      :busy="store.submitting"
      :collections="collectionsStore.sortedList"
      @submit="handleEditSubmit"
      @cancel="closeEdit"
    />
    <ConfirmDialog
      :open="deleteDialog.open"
      :busy="deleteDialog.busy"
      title="删除闪记"
      :message="deleteDialog.target ? `确认删除“${deleteDialog.target.title}”吗？此操作不可撤销。` : ''"
      confirm-label="删除"
      danger
      @confirm="confirmDelete"
      @cancel="cancelDelete"
    />

    <!-- W22-01 右下角悬浮 FAB -->
    <button
      ref="fabBtnEl"
      type="button"
      class="fab"
      :class="{ mobile: isMobileLayout, busy: quickUploading }"
      :disabled="quickUploading"
      :aria-label="'快速捕获'"
      :title="'快速捕获：文字 / 图片 / 视频 / 文件'"
      @click="openFabMenu"
    >+</button>

    <!-- 菜单复用 MessageActionMenu：发现样式 · ESC / 点空白关闭 -->
    <MessageActionMenu
      v-model:open="fabMenuOpen"
      :x="fabMenuX"
      :y="fabMenuY"
      :items="fabMenuItems"
      :menu-width="180"
      @select="onFabMenuSelect"
    />

    <MessageActionMenu
      v-model:open="actionMenuOpen"
      :x="actionMenuX"
      :y="actionMenuY"
      :items="noteActionMenuItems"
      @select="onNoteActionSelect"
    />

    <!-- 隐藏的多用 file input：按现场设置 accept / capture -->
    <input
      ref="hiddenFileInput"
      type="file"
      class="hidden-file"
      :accept="hiddenFileAccept"
      :capture="hiddenFileCapture || null"
      @change="onHiddenFileChange"
    />

    <!-- W22-02 快记文本 -->
    <QuickCaptureDialog
      ref="quickDialogRef"
      v-model:open="quickDialog.open"
      :busy="quickDialog.busy"
      title="快速捕获·文字"
      description="内容会发送到收集箱"
      @submit="handleQuickTextSubmit"
      @cancel="handleQuickTextCancel"
    />

    <!-- D1-W26-02 多媒体卡片：目标固定为收集箱（flashNoteId=-1） -->
    <CardEditorDialog
      v-model:open="cardEditor.open"
      :flash-note-id="INBOX_FLASH_NOTE_ID"
      @created="onCardEditorCreated"
    />
  </div>
</template>

<style scoped>
.notes-page {
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
.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.page-stats {
  font-size: 13px;
  color: var(--color-text-secondary);
}
.btn-search-toggle {
  width: 36px;
  height: 36px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-surface);
  cursor: pointer;
}
.btn-search-toggle.active {
  border-color: var(--color-primary);
  color: var(--color-primary);
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
  display: flex;
  flex-direction: column;
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-divider);
  overflow: hidden;
}
.inline-search {
  display: flex;
  gap: 8px;
  align-items: center;
  background: var(--color-surface);
  border: 1px solid var(--color-divider);
  border-radius: var(--radius-md);
  padding: 8px;
}
.inline-search-input {
  flex: 1;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 8px 12px;
}
.inline-search-btn,
.inline-search-close {
  border: none;
  border-radius: var(--radius-md);
  padding: 8px 12px;
  cursor: pointer;
}
.inline-search-btn {
  background: var(--color-primary);
  color: #fff;
}
.inline-search-close {
  background: var(--color-bg);
  color: var(--color-text-secondary);
}
.note-swipe-shell {
  position: relative;
  overflow: hidden;
}
.swipe-delete-action {
  position: absolute;
  inset: 0 0 0 auto;
  width: 96px;
  border: none;
  background: var(--color-danger);
  color: #fff;
  font-weight: 600;
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
.caret {
  font-size: 12px;
  color: var(--color-text-hint);
}

.note-item {
  position: relative;
  display: grid;
  grid-template-columns: 40px 1fr auto;
  grid-template-rows: auto auto;
  grid-template-areas:
    'icon meta time'
    '. actions actions';
  align-items: center;
  gap: 8px 12px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--color-divider);
  transition: transform 0.16s ease;
}
.note-item:last-child {
  border-bottom: none;
}
.note-item.dimmed {
  opacity: 0.65;
}
.note-item.inbox {
  background: var(--color-primary-light);
}
.note-item.clickable {
  cursor: pointer;
  transition: background 0.12s;
}
.note-item.clickable:hover {
  background: var(--color-bg);
}
.note-item.inbox.clickable:hover {
  background: var(--color-primary-light);
  filter: brightness(0.97);
}
.note-icon {
  grid-area: icon;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: var(--radius-md);
  background: var(--color-bg);
  font-size: 22px;
}
.note-meta {
  grid-area: meta;
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.note-title {
  margin: 0;
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.badge-pinned {
  font-size: 12px;
  margin-right: 2px;
}
.note-preview {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-hint);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.note-time {
  grid-area: time;
  font-size: 11px;
  color: var(--color-text-hint);
  white-space: nowrap;
}
.note-actions {
  grid-area: actions;
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
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
/* D1-W28-12 搜索结果项布局：原先用 grid 但没有 grid-template-areas，
   而 .note-icon / .note-meta 都依赖 `grid-area: icon/meta`，导致两者
   被放到隐式 grid 单元格而不是同一行 → 排版错乱。改用 flex，
   让子元素自然按横向排列，icon 自身仍是 40×40 圆角块。 */
.search-hit {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--color-divider);
  cursor: pointer;
  transition: background 0.12s;
}
.search-hit:hover {
  background: var(--color-bg);
}
.search-hit:last-child {
  border-bottom: none;
}
/* search-hit 里 .note-icon 不再依赖 grid-area；保留 40x40 圆角块外形 */
.search-hit .note-icon {
  flex-shrink: 0;
}
.search-hit .note-meta {
  flex: 1;
  min-width: 0;
}

@media (max-width: 768px) {
  .note-actions .action {
    padding: 4px 8px;
    font-size: 11px;
  }
}

/* W22-01 FAB 悬浮按钮 */
.fab {
  position: fixed;
  right: 24px;
  bottom: 24px;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  border: none;
  background: var(--color-primary);
  color: #ffffff;
  font-size: 30px;
  line-height: 1;
  cursor: pointer;
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.18);
  z-index: 50;
  transition: transform 0.12s, box-shadow 0.12s;
}
.fab:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 8px 22px rgba(0, 0, 0, 0.22);
}
.fab:active:not(:disabled) {
  transform: translateY(0);
}
.fab:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.fab.mobile {
  /* 移动端避开底部 tab bar（8 + 56 = 64） */
  bottom: 80px;
}
.hidden-file {
  display: none;
}
</style>

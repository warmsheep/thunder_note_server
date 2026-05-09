<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useContactsStore } from '../stores/contacts'
import { useToast } from '../composables/useToast'
import LoadingState from '../components/LoadingState.vue'
import ErrorState from '../components/ErrorState.vue'
import EmptyState from '../components/EmptyState.vue'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import AuthenticatedAvatar from '../components/AuthenticatedAvatar.vue'

// D1-W14 联系人与好友请求页
// - 头部 tab：联系人 / 好友请求（带 pending 计数）
// - 联系人列表：FRIEND 显示删除入口；PENDING_SENT 显示"等待对方接受 + 撤销"
//   （注：后端 ContactUserDto 不带 requestId，本期撤销/删除统一走 deleteContact 接口；
//    后端会同时清理 FRIEND 和 PENDING_SENT 的关系记录）
// - 好友请求列表：accept/reject
// - 顶部"添加好友"按钮 → 搜索弹窗 → 发起请求

const router = useRouter()
const store = useContactsStore()
const { showSuccess, showError } = useToast()

// D1-W20-02 开始聊天：仅对 FRIEND 状态启用；点击 → 跳到联系人 1v1 会话路由
function openContactChat(contact) {
  if (!contact || contact.userId == null) return
  if (contact.relationStatus !== 'FRIEND') return
  router.push({ name: 'contact-chat', params: { peerUserId: String(contact.userId) } })
}

const activeTab = ref('contacts') // contacts | requests
const showSearch = ref(false)
const searchInput = ref('')

const removeDialog = ref({ open: false, target: null, busy: false, isPending: false })
const requestActionBusyId = ref(null)

const initialLoading = computed(
  () => (activeTab.value === 'contacts' ? !store.contactsLoaded : !store.requestsLoaded)
    && (activeTab.value === 'contacts' ? store.contactsLoading : store.requestsLoading)
)
const showErrorState = computed(
  () => Boolean(store.error)
    && (activeTab.value === 'contacts' ? !store.contactsLoaded : !store.requestsLoaded)
)

onMounted(async () => {
  if (!store.contactsLoaded) {
    store.fetchContacts().catch(() => {})
  }
  if (!store.requestsLoaded) {
    store.fetchFriendRequests().catch(() => {})
  }
  store.fetchPendingCount()
})

function switchTab(tab) {
  activeTab.value = tab
  if (tab === 'contacts' && !store.contactsLoaded) {
    store.fetchContacts().catch(() => {})
  }
  if (tab === 'requests' && !store.requestsLoaded) {
    store.fetchFriendRequests().catch(() => {})
  }
}

function handleRetry() {
  if (activeTab.value === 'contacts') {
    store.fetchContacts().catch(() => {})
  } else {
    store.fetchFriendRequests().catch(() => {})
  }
}

function openSearch() {
  searchInput.value = ''
  store.clearSearch()
  showSearch.value = true
}

function closeSearch() {
  showSearch.value = false
}

async function doSearch() {
  try {
    await store.search(searchInput.value)
  } catch (e) {
    showError(e?.serverMessage || e?.message || '搜索失败')
  }
}

async function sendFriendRequest(target) {
  if (!target || target.userId == null) return
  try {
    await store.sendRequest(target.userId)
    showSuccess('好友请求已发送')
  } catch (e) {
    showError(e?.serverMessage || e?.message || '发送好友请求失败')
  }
}

async function acceptRequest(req) {
  if (!req || req.requestId == null) return
  requestActionBusyId.value = req.requestId
  try {
    await store.acceptRequest(req.requestId)
    showSuccess(`已接受 ${req.nickname || req.username || ''} 的好友请求`)
  } catch (e) {
    showError(e?.serverMessage || e?.message || '操作失败')
  } finally {
    requestActionBusyId.value = null
  }
}

async function rejectRequest(req) {
  if (!req || req.requestId == null) return
  requestActionBusyId.value = req.requestId
  try {
    await store.rejectRequest(req.requestId)
    showSuccess('已拒绝')
  } catch (e) {
    showError(e?.serverMessage || e?.message || '操作失败')
  } finally {
    requestActionBusyId.value = null
  }
}

function askRemoveContact(contact) {
  if (!contact || contact.userId == null) return
  removeDialog.value = {
    open: true,
    target: contact,
    busy: false,
    isPending: contact.relationStatus === 'PENDING_SENT'
  }
}

async function confirmRemoveContact() {
  const target = removeDialog.value.target
  if (!target) return
  removeDialog.value.busy = true
  try {
    await store.removeContact(target.userId)
    showSuccess(removeDialog.value.isPending ? '已撤销好友请求' : '已删除联系人')
    removeDialog.value = { open: false, target: null, busy: false, isPending: false }
  } catch (e) {
    showError(e?.serverMessage || e?.message || '操作失败')
    removeDialog.value.busy = false
  }
}

function cancelRemove() {
  if (removeDialog.value.busy) return
  removeDialog.value = { open: false, target: null, busy: false, isPending: false }
}

function relationLabel(status) {
  switch (status) {
    case 'FRIEND': return ''
    case 'PENDING_SENT': return '等待对方接受'
    case 'PENDING_RECEIVED': return '对方等你处理'
    default: return ''
  }
}

function searchActionLabel(status) {
  switch (status) {
    case 'FRIEND': return '已是好友'
    case 'PENDING_SENT': return '请求已发送'
    case 'PENDING_RECEIVED': return '在好友请求中处理'
    default: return '加为好友'
  }
}

function searchActionDisabled(status) {
  return status !== 'NONE'
}

function nameOf(o) {
  if (!o) return ''
  return o.nickname || o.username || `用户 ${o.userId ?? ''}`
}
</script>

<template>
  <div class="contacts-page">
    <header class="page-toolbar">
      <div class="tabs">
        <button
          type="button"
          class="tab"
          :class="{ active: activeTab === 'contacts' }"
          @click="switchTab('contacts')"
        >联系人 ({{ store.contactsCount }})</button>
        <button
          type="button"
          class="tab"
          :class="{ active: activeTab === 'requests' }"
          @click="switchTab('requests')"
        >
          好友请求
          <span v-if="store.pendingCount > 0" class="badge">{{ store.pendingCount }}</span>
        </button>
      </div>
      <button type="button" class="btn-primary" @click="openSearch">+ 添加好友</button>
    </header>

    <LoadingState v-if="initialLoading" text="加载中..." />
    <ErrorState
      v-else-if="showErrorState"
      :message="store.error"
      @retry="handleRetry"
    />
    <template v-else>
      <!-- 联系人 tab -->
      <section v-if="activeTab === 'contacts'" class="list">
        <EmptyState
          v-if="store.contacts.length === 0"
          icon="👥"
          title="还没有联系人"
          description='点击右上角“添加好友”搜索其他用户'
        />
        <article
          v-for="c in store.contacts"
          :key="c.userId"
          class="row"
        >
          <AuthenticatedAvatar
            :avatar="c.avatar"
            :fallback="nameOf(c).slice(0, 1).toUpperCase()"
            :size="44"
          />
          <div class="row-meta">
            <p class="row-title">{{ nameOf(c) }}</p>
            <p class="row-sub">
              <span v-if="c.username && c.username !== c.nickname">@{{ c.username }}</span>
              <span v-if="c.relationStatus === 'PENDING_SENT'" class="status-pending">
                · {{ relationLabel(c.relationStatus) }}
              </span>
            </p>
          </div>
          <div class="row-actions">
            <button
              v-if="c.relationStatus === 'FRIEND'"
              type="button"
              class="btn-primary-outline"
              :disabled="store.submitting"
              :title="`与 ${nameOf(c)} 聊天`"
              @click="openContactChat(c)"
            >💬 聊天</button>
            <button
              type="button"
              class="btn-danger-outline"
              :disabled="store.submitting"
              @click="askRemoveContact(c)"
            >{{ c.relationStatus === 'PENDING_SENT' ? '撤销请求' : '删除' }}</button>
          </div>
        </article>
      </section>

      <!-- 好友请求 tab -->
      <section v-else class="list">
        <EmptyState
          v-if="store.friendRequests.length === 0"
          icon="📬"
          title="暂无好友请求"
          description="别人向你发起好友请求时会出现在这里"
        />
        <article
          v-for="r in store.friendRequests"
          :key="r.requestId"
          class="row"
        >
          <AuthenticatedAvatar
            :avatar="r.avatar"
            :fallback="nameOf(r).slice(0, 1).toUpperCase()"
            :size="44"
          />
          <div class="row-meta">
            <p class="row-title">{{ nameOf(r) }}</p>
            <p class="row-sub">
              <span v-if="r.username && r.username !== r.nickname">@{{ r.username }}</span>
              <span class="status-receive">· 想加你为好友</span>
            </p>
          </div>
          <div class="actions">
            <button
              type="button"
              class="btn-primary"
              :disabled="requestActionBusyId === r.requestId"
              @click="acceptRequest(r)"
            >接受</button>
            <button
              type="button"
              class="btn-secondary"
              :disabled="requestActionBusyId === r.requestId"
              @click="rejectRequest(r)"
            >拒绝</button>
          </div>
        </article>
      </section>
    </template>

    <!-- 搜索好友弹窗 -->
    <div v-if="showSearch" class="modal-overlay" @click.self="closeSearch">
      <div class="modal" role="dialog" aria-label="添加好友">
        <header class="modal-header">
          <h2 class="modal-title">添加好友</h2>
          <button type="button" class="modal-close" @click="closeSearch">×</button>
        </header>
        <div class="modal-body">
          <div class="search-bar">
            <input
              v-model="searchInput"
              type="text"
              class="search-input"
              placeholder="搜索用户名或昵称"
              @keydown.enter.prevent="doSearch"
            />
            <button
              type="button"
              class="btn-primary"
              :disabled="store.searchLoading || !searchInput.trim()"
              @click="doSearch"
            >{{ store.searchLoading ? '搜索中...' : '搜索' }}</button>
          </div>

          <p v-if="store.searchError" class="form-error">{{ store.searchError }}</p>

          <ul v-if="store.searchResults.length" class="search-results">
            <li
              v-for="u in store.searchResults"
              :key="u.userId"
              class="search-row"
            >
              <AuthenticatedAvatar
                :avatar="u.avatar"
                :fallback="nameOf(u).slice(0, 1).toUpperCase()"
                :size="40"
              />
              <div class="search-meta">
                <p class="row-title">{{ nameOf(u) }}</p>
                <p class="row-sub">
                  <span v-if="u.username && u.username !== u.nickname">@{{ u.username }}</span>
                </p>
              </div>
              <button
                type="button"
                class="btn-secondary"
                :disabled="searchActionDisabled(u.relationStatus) || store.submitting"
                @click="sendFriendRequest(u)"
              >{{ searchActionLabel(u.relationStatus) }}</button>
            </li>
          </ul>
          <p
            v-else-if="store.searchHasRun && !store.searchLoading && !store.searchError"
            class="empty-text"
          >未匹配到用户</p>
        </div>
      </div>
    </div>

    <ConfirmDialog
      :open="removeDialog.open"
      :busy="removeDialog.busy"
      :title="removeDialog.isPending ? '撤销好友请求' : '删除联系人'"
      :message="removeDialog.isPending
        ? ('撤销向 ' + nameOf(removeDialog.target) + ' 发出的好友请求？')
        : ('从联系人中删除 ' + nameOf(removeDialog.target) + '？此操作不可撤销。')"
      confirm-label="确认"
      :danger="true"
      @confirm="confirmRemoveContact"
      @cancel="cancelRemove"
    />
  </div>
</template>

<style scoped>
.contacts-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.tabs {
  display: inline-flex;
  background: var(--color-surface);
  border: 1px solid var(--color-divider);
  border-radius: var(--radius-md);
  padding: 2px;
}
.tab {
  border: none;
  background: transparent;
  padding: 6px 14px;
  border-radius: var(--radius-sm);
  font-size: 13px;
  color: var(--color-text-secondary);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.tab.active {
  background: var(--color-primary);
  color: #ffffff;
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
}
.tab.active .badge {
  background: #ffffff;
  color: var(--color-primary);
}

.list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.row {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 12px;
  align-items: center;
  padding: 12px 16px;
  background: var(--color-surface);
  border: 1px solid var(--color-divider);
  border-radius: var(--radius-lg);
}
.row-meta {
  min-width: 0;
}
.row-title {
  margin: 0;
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-primary);
  word-break: break-word;
}
.row-sub {
  margin: 2px 0 0 0;
  font-size: 12px;
  color: var(--color-text-hint);
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.status-pending { color: #b45309; }
.status-receive { color: var(--color-primary); }

.actions {
  display: flex;
  gap: 6px;
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
  opacity: 0.55;
  cursor: not-allowed;
}
.btn-secondary {
  padding: 6px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  color: var(--color-text-secondary);
  font-size: 13px;
  cursor: pointer;
}
.btn-secondary:hover:not(:disabled) {
  border-color: var(--color-primary);
  color: var(--color-primary);
}
.btn-secondary:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
/* W20-02 联系人卡片右侧操作组：聊天 + 删除 */
.row-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: none;
}
.btn-primary-outline {
  padding: 6px 12px;
  border: 1px solid var(--color-primary);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  color: var(--color-primary);
  font-size: 12px;
  cursor: pointer;
  white-space: nowrap;
}
.btn-primary-outline:hover:not(:disabled) {
  background: var(--color-primary-light, rgba(46, 125, 50, 0.08));
}
.btn-primary-outline:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.btn-danger-outline {
  padding: 6px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  color: var(--color-text-secondary);
  font-size: 12px;
  cursor: pointer;
}
.btn-danger-outline:hover:not(:disabled) {
  border-color: var(--color-danger);
  color: var(--color-danger);
}
.btn-danger-outline:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

/* 搜索弹窗 */
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
  width: min(480px, 100%);
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  box-shadow: var(--shadow-md);
  overflow: hidden;
}
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
.modal-close:hover { background: var(--color-text-secondary); color: var(--color-surface); }
.modal-body {
  padding: 16px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.search-bar {
  display: flex;
  gap: 8px;
}
.search-input {
  flex: 1;
  padding: 8px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  font-size: 14px;
  outline: none;
  background: var(--color-surface);
  color: var(--color-text-primary);
}
.search-input:focus { border-color: var(--color-primary); }
.form-error {
  margin: 0;
  color: var(--color-danger);
  font-size: 12px;
}

.search-results {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.search-row {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 10px;
  align-items: center;
  padding: 8px;
  border: 1px solid var(--color-divider);
  border-radius: var(--radius-md);
}
.search-meta { min-width: 0; }
.empty-text {
  margin: 0;
  text-align: center;
  color: var(--color-text-hint);
  font-size: 13px;
  padding: 16px 0;
}
</style>

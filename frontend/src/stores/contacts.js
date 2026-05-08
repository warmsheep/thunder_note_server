import { defineStore } from 'pinia'
import {
  listContacts as listContactsApi,
  listFriendRequests as listFriendRequestsApi,
  countFriendRequests as countFriendRequestsApi,
  searchContacts as searchContactsApi,
  sendFriendRequest as sendFriendRequestApi,
  acceptFriendRequest as acceptFriendRequestApi,
  rejectFriendRequest as rejectFriendRequestApi,
  cancelFriendRequest as cancelFriendRequestApi,
  deleteContact as deleteContactApi
} from '../api/users'

// D1-W14 联系人与好友请求 store
// - contacts: 已是好友 + 我发出尚未接受的（relationStatus FRIEND/PENDING_SENT）
// - friendRequests: 别人发给我的待处理请求
// - pendingCount: 主壳徽标用，单独由 countFriendRequests 维护，避免列表未拉时也能展示
// - searchResults / searchRunId: 搜索用户结果，runId 守卫并发
//
// 操作类（accept/reject/cancel/sendFriend/deleteContact）成功后联动刷新列表 + count。

export const useContactsStore = defineStore('contacts', {
  state: () => ({
    contacts: [],
    friendRequests: [],
    pendingCount: 0,

    contactsLoading: false,
    requestsLoading: false,
    contactsLoaded: false,
    requestsLoaded: false,
    error: null,
    submitting: false,

    searchKeyword: '',
    searchResults: [],
    searchLoading: false,
    searchError: null,
    searchRunId: 0,
    searchHasRun: false
  }),

  getters: {
    contactsCount: (state) => state.contacts.length,
    requestsCount: (state) => state.friendRequests.length,
    findContactById: (state) => (userId) =>
      state.contacts.find((c) => c && Number(c.userId) === Number(userId)) || null
  },

  actions: {
    async fetchContacts({ silent = false } = {}) {
      if (!silent) this.contactsLoading = true
      this.error = null
      try {
        const data = await listContactsApi()
        this.contacts = Array.isArray(data) ? data : []
        this.contactsLoaded = true
        return this.contacts
      } catch (e) {
        this.error = e?.serverMessage || e?.message || '加载联系人失败'
        throw e
      } finally {
        if (!silent) this.contactsLoading = false
      }
    },

    async fetchFriendRequests({ silent = false } = {}) {
      if (!silent) this.requestsLoading = true
      this.error = null
      try {
        const data = await listFriendRequestsApi()
        this.friendRequests = Array.isArray(data) ? data : []
        this.requestsLoaded = true
        return this.friendRequests
      } catch (e) {
        this.error = e?.serverMessage || e?.message || '加载好友请求失败'
        throw e
      } finally {
        if (!silent) this.requestsLoading = false
      }
    },

    async fetchPendingCount() {
      try {
        const value = await countFriendRequestsApi()
        const n = Number(value)
        this.pendingCount = Number.isFinite(n) && n >= 0 ? n : 0
        return this.pendingCount
      } catch (_e) {
        // 静默：徽标失败不应阻塞主流程
        return this.pendingCount
      }
    },

    async search(keyword) {
      const k = keyword == null ? this.searchKeyword : String(keyword)
      this.searchKeyword = k
      const trimmed = k.trim()
      if (!trimmed) {
        this.searchResults = []
        this.searchError = null
        this.searchLoading = false
        this.searchHasRun = false
        this.searchRunId += 1
        return []
      }
      this.searchHasRun = true
      this.searchError = null
      this.searchLoading = true
      const myRunId = ++this.searchRunId
      try {
        const data = await searchContactsApi(trimmed)
        if (myRunId !== this.searchRunId) return this.searchResults
        this.searchResults = Array.isArray(data) ? data : []
        return this.searchResults
      } catch (e) {
        if (myRunId !== this.searchRunId) return this.searchResults
        this.searchError = e?.serverMessage || e?.message || '搜索失败'
        this.searchResults = []
        throw e
      } finally {
        if (myRunId === this.searchRunId) this.searchLoading = false
      }
    },

    clearSearch() {
      this.searchRunId += 1
      this.searchKeyword = ''
      this.searchResults = []
      this.searchError = null
      this.searchLoading = false
      this.searchHasRun = false
    },

    async sendRequest(targetUserId) {
      this.submitting = true
      try {
        await sendFriendRequestApi(targetUserId)
        // 刷新搜索结果使 relationStatus 变成 PENDING_SENT
        if (this.searchKeyword) {
          this.search(this.searchKeyword).catch(() => {})
        }
        // 刷新联系人列表（PENDING_SENT 项也会出现在那里）
        this.fetchContacts({ silent: true }).catch(() => {})
      } finally {
        this.submitting = false
      }
    },

    async acceptRequest(requestId) {
      this.submitting = true
      try {
        await acceptFriendRequestApi(requestId)
        // 本地优化：从 friendRequests 移除
        this.friendRequests = this.friendRequests.filter((r) => r && r.requestId !== requestId)
        await Promise.allSettled([
          this.fetchPendingCount(),
          this.fetchContacts({ silent: true })
        ])
      } finally {
        this.submitting = false
      }
    },

    async rejectRequest(requestId) {
      this.submitting = true
      try {
        await rejectFriendRequestApi(requestId)
        this.friendRequests = this.friendRequests.filter((r) => r && r.requestId !== requestId)
        await this.fetchPendingCount()
      } finally {
        this.submitting = false
      }
    },

    async cancelRequest(requestId) {
      // 撤销自己发出的好友请求；关联的 contact 项 relationStatus=PENDING_SENT 应消失
      this.submitting = true
      try {
        await cancelFriendRequestApi(requestId)
        await this.fetchContacts({ silent: true })
      } finally {
        this.submitting = false
      }
    },

    async removeContact(contactUserId) {
      this.submitting = true
      try {
        await deleteContactApi(contactUserId)
        this.contacts = this.contacts.filter((c) => c && Number(c.userId) !== Number(contactUserId))
      } finally {
        this.submitting = false
      }
    },

    reset() {
      this.contacts = []
      this.friendRequests = []
      this.pendingCount = 0
      this.contactsLoading = false
      this.requestsLoading = false
      this.contactsLoaded = false
      this.requestsLoaded = false
      this.error = null
      this.submitting = false
      this.clearSearch()
    }
  }
})

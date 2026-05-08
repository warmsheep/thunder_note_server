import { defineStore } from 'pinia'
import { searchFlashNotes } from '../api/flashNotes'

// D1-W10 搜索 store
// - 单一活跃搜索：runId 守卫避免旧请求覆盖新结果
// - 空 query 不发请求，直接清空结果
// - results: { noteNameMatched: [], messageContentMatched: [] }
export const useSearchStore = defineStore('search', {
  state: () => ({
    query: '',
    activeQuery: '',
    loading: false,
    error: null,
    results: { noteNameMatched: [], messageContentMatched: [] },
    runId: 0,
    hasSearched: false
  }),

  getters: {
    noteHits: (state) => state.results.noteNameMatched || [],
    messageHits: (state) => state.results.messageContentMatched || [],
    totalHits: (state) =>
      (state.results.noteNameMatched?.length || 0) +
      (state.results.messageContentMatched?.length || 0),
    isEmptyAfterSearch: (state) =>
      state.hasSearched &&
      !state.loading &&
      !state.error &&
      ((state.results.noteNameMatched?.length || 0) === 0) &&
      ((state.results.messageContentMatched?.length || 0) === 0)
  },

  actions: {
    setQuery(query) {
      this.query = query == null ? '' : String(query)
    },

    async search(rawQuery) {
      const q = (rawQuery == null ? this.query : String(rawQuery)).trim()
      if (!q) {
        this.clear()
        return
      }
      this.activeQuery = q
      this.error = null
      this.loading = true
      this.hasSearched = true
      const myRunId = ++this.runId
      try {
        const data = await searchFlashNotes(q)
        // 旧请求被新搜索覆盖，丢弃结果
        if (myRunId !== this.runId) return
        this.results = {
          noteNameMatched: Array.isArray(data?.noteNameMatched) ? data.noteNameMatched : [],
          messageContentMatched: Array.isArray(data?.messageContentMatched) ? data.messageContentMatched : []
        }
      } catch (e) {
        if (myRunId !== this.runId) return
        this.error = e?.serverMessage || e?.message || '搜索失败'
        this.results = { noteNameMatched: [], messageContentMatched: [] }
      } finally {
        if (myRunId === this.runId) {
          this.loading = false
        }
      }
    },

    clear() {
      this.runId += 1 // 让进行中的请求结果作废
      this.activeQuery = ''
      this.error = null
      this.loading = false
      this.results = { noteNameMatched: [], messageContentMatched: [] }
      this.hasSearched = false
    },

    reset() {
      this.query = ''
      this.clear()
    }
  }
})

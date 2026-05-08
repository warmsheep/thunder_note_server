import { defineStore } from 'pinia'
import {
  listCollections,
  createCollection,
  updateCollection,
  deleteCollection
} from '../api/collections'

// D1-W7 合集 store。结构与 flashNotes store 对齐：单 list + 加载态 + 错误。
export const useCollectionsStore = defineStore('collections', {
  state: () => ({
    list: [],
    loading: false,
    submitting: false,
    error: null,
    loaded: false
  }),

  getters: {
    sortedList: (state) => {
      // 按 name 字典序展示，便于查找
      return state.list.slice().sort((a, b) => {
        const na = (a && a.name) || ''
        const nb = (b && b.name) || ''
        return na.localeCompare(nb, 'zh-Hans-CN')
      })
    },
    findById: (state) => (id) => state.list.find((c) => c && c.id === id) || null,
    findByName: (state) => (name) => state.list.find((c) => c && c.name === name) || null
  },

  actions: {
    async fetchList({ silent = false } = {}) {
      if (!silent) this.loading = true
      this.error = null
      try {
        const data = await listCollections()
        this.list = Array.isArray(data) ? data : []
        this.loaded = true
        return this.list
      } catch (e) {
        this.error = e?.serverMessage || e?.message || '加载失败'
        throw e
      } finally {
        if (!silent) this.loading = false
      }
    },

    async create(payload) {
      this.submitting = true
      this.error = null
      try {
        const created = await createCollection(payload)
        if (created) this.list = [created, ...this.list]
        return created
      } catch (e) {
        this.error = e?.serverMessage || e?.message || '创建失败'
        throw e
      } finally {
        this.submitting = false
      }
    },

    async update(id, payload) {
      this.submitting = true
      this.error = null
      try {
        const updated = await updateCollection(id, payload)
        this.replaceLocal(updated)
        return updated
      } catch (e) {
        this.error = e?.serverMessage || e?.message || '保存失败'
        throw e
      } finally {
        this.submitting = false
      }
    },

    async remove(id) {
      try {
        await deleteCollection(id)
        this.list = this.list.filter((c) => c && c.id !== id)
      } catch (e) {
        this.error = e?.serverMessage || e?.message || '删除失败'
        throw e
      }
    },

    replaceLocal(updated) {
      if (!updated || updated.id == null) return
      const idx = this.list.findIndex((c) => c && c.id === updated.id)
      if (idx >= 0) {
        const next = this.list.slice()
        next[idx] = updated
        this.list = next
      } else {
        this.list = [updated, ...this.list]
      }
    },

    reset() {
      this.list = []
      this.loading = false
      this.submitting = false
      this.error = null
      this.loaded = false
    }
  }
})

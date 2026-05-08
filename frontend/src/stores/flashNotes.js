import { defineStore } from 'pinia'
import {
  listFlashNotes,
  createFlashNote,
  updateFlashNote,
  setFlashNotePinned,
  setFlashNoteHidden,
  deleteFlashNote
} from '../api/flashNotes'

// D1-W5 闪记列表 store
// - 单一事实源：list 数组保存当前用户的全部闪记（不含 deleted）
// - 分组通过 getter 派生：inbox / pinnedList / normalList / hiddenList
// - 排序：pinned/normal 按 updatedAt 倒序；inbox 永远在最上方

function compareByUpdatedDesc(a, b) {
  const ta = a.updatedAt ? Date.parse(a.updatedAt) : 0
  const tb = b.updatedAt ? Date.parse(b.updatedAt) : 0
  return tb - ta
}

export const useFlashNotesStore = defineStore('flashNotes', {
  state: () => ({
    list: [],
    loading: false,
    submitting: false,
    error: null,
    loaded: false
  }),

  getters: {
    inboxNote: (state) => state.list.find((n) => n && n.inbox) || null,
    pinnedList: (state) =>
      state.list
        .filter((n) => n && !n.inbox && !n.hidden && n.pinned)
        .slice()
        .sort(compareByUpdatedDesc),
    normalList: (state) =>
      state.list
        .filter((n) => n && !n.inbox && !n.hidden && !n.pinned)
        .slice()
        .sort(compareByUpdatedDesc),
    hiddenList: (state) =>
      state.list
        .filter((n) => n && !n.inbox && n.hidden)
        .slice()
        .sort(compareByUpdatedDesc),
    visibleCount: (state) => state.list.filter((n) => n && !n.hidden).length
  },

  actions: {
    async fetchList({ silent = false } = {}) {
      if (!silent) {
        this.loading = true
      }
      this.error = null
      try {
        const data = await listFlashNotes()
        this.list = Array.isArray(data) ? data : []
        this.loaded = true
        return this.list
      } catch (e) {
        this.error = e?.serverMessage || e?.message || '加载失败'
        throw e
      } finally {
        if (!silent) {
          this.loading = false
        }
      }
    },

    async create(payload) {
      this.submitting = true
      this.error = null
      try {
        const created = await createFlashNote(payload)
        if (created) {
          this.list = [created, ...this.list]
        }
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
        const updated = await updateFlashNote(id, payload)
        this.replaceLocal(updated)
        return updated
      } catch (e) {
        this.error = e?.serverMessage || e?.message || '保存失败'
        throw e
      } finally {
        this.submitting = false
      }
    },

    async setPinned(id, pinned) {
      try {
        const updated = await setFlashNotePinned(id, pinned)
        this.replaceLocal(updated)
        return updated
      } catch (e) {
        this.error = e?.serverMessage || e?.message || '操作失败'
        throw e
      }
    },

    async setHidden(id, hidden) {
      try {
        const updated = await setFlashNoteHidden(id, hidden)
        this.replaceLocal(updated)
        return updated
      } catch (e) {
        this.error = e?.serverMessage || e?.message || '操作失败'
        throw e
      }
    },

    async remove(id) {
      try {
        await deleteFlashNote(id)
        this.list = this.list.filter((n) => n && n.id !== id)
      } catch (e) {
        this.error = e?.serverMessage || e?.message || '删除失败'
        throw e
      }
    },

    replaceLocal(updated) {
      if (!updated || updated.id == null) return
      const idx = this.list.findIndex((n) => n && n.id === updated.id)
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

import { defineStore } from 'pinia'
import {
  listFavorites,
  addFavorite as addFavoriteApi,
  removeFavorite as removeFavoriteApi
} from '../api/favorites'

// D1-W8 收藏 store
// - list 是 FavoriteMessageItem 数组，按 favoritedAt 倒序展示
// - favoritedSet 缓存所有已收藏的 messageId（数字）便于 O(1) 判断
// - actions：fetchList / add(messageId) / remove(messageId)
//   add 的 optimistic：立即把 messageId 写入 favoritedSet；失败回滚

function compareByFavoritedAtDesc(a, b) {
  const ta = a && a.favoritedAt ? Date.parse(a.favoritedAt) : 0
  const tb = b && b.favoritedAt ? Date.parse(b.favoritedAt) : 0
  return tb - ta
}

export const useFavoritesStore = defineStore('favorites', {
  state: () => ({
    list: [],
    favoritedSet: new Set(),
    loading: false,
    submitting: false,
    error: null,
    loaded: false
  }),

  getters: {
    sortedList: (state) => state.list.slice().sort(compareByFavoritedAtDesc),
    isFavorited: (state) => (messageId) =>
      messageId != null && state.favoritedSet.has(Number(messageId))
  },

  actions: {
    async fetchList({ silent = false } = {}) {
      if (!silent) this.loading = true
      this.error = null
      try {
        const data = await listFavorites()
        this.list = Array.isArray(data) ? data : []
        const next = new Set()
        for (const item of this.list) {
          if (item && item.messageId != null) {
            next.add(Number(item.messageId))
          }
        }
        this.favoritedSet = next
        this.loaded = true
        return this.list
      } catch (e) {
        this.error = e?.serverMessage || e?.message || '加载收藏失败'
        throw e
      } finally {
        if (!silent) this.loading = false
      }
    },

    async add(messageId) {
      if (messageId == null) return
      const numericId = Number(messageId)
      // optimistic：先标已收藏
      const before = this.favoritedSet.has(numericId)
      if (!before) {
        const next = new Set(this.favoritedSet)
        next.add(numericId)
        this.favoritedSet = next
      }
      this.submitting = true
      try {
        const item = await addFavoriteApi(messageId)
        if (item) {
          this.list = [item, ...this.list.filter((x) => x && x.messageId !== numericId)]
        }
        return item
      } catch (e) {
        // 回滚
        if (!before) {
          const next = new Set(this.favoritedSet)
          next.delete(numericId)
          this.favoritedSet = next
        }
        this.error = e?.serverMessage || e?.message || '收藏失败'
        throw e
      } finally {
        this.submitting = false
      }
    },

    async remove(messageId) {
      if (messageId == null) return
      const numericId = Number(messageId)
      const before = this.favoritedSet.has(numericId)
      // optimistic：先移除
      if (before) {
        const next = new Set(this.favoritedSet)
        next.delete(numericId)
        this.favoritedSet = next
      }
      const removedItems = this.list.filter((x) => x && x.messageId === numericId)
      this.list = this.list.filter((x) => x && x.messageId !== numericId)

      this.submitting = true
      try {
        await removeFavoriteApi(messageId)
      } catch (e) {
        // 回滚
        if (before) {
          const next = new Set(this.favoritedSet)
          next.add(numericId)
          this.favoritedSet = next
        }
        if (removedItems.length) {
          this.list = [...removedItems, ...this.list]
        }
        this.error = e?.serverMessage || e?.message || '取消收藏失败'
        throw e
      } finally {
        this.submitting = false
      }
    },

    reset() {
      this.list = []
      this.favoritedSet = new Set()
      this.loading = false
      this.submitting = false
      this.error = null
      this.loaded = false
    }
  }
})

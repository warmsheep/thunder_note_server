import { defineStore } from 'pinia'
import { getProfile, updateProfile, updateAvatar } from '../api/users'

// D1-W11 用户资料 store
// - profile 来自 POST /api/users/profile（含 nickname/bio/avatar/preferencesJson 等）
// - update 系列回写后更新 state，调用方负责同步 authStore.user 让 displayName/头像跨页面一致
export const useProfileStore = defineStore('profile', {
  state: () => ({
    profile: null,
    loading: false,
    saving: false,
    error: null,
    loaded: false
  }),

  getters: {
    nickname: (state) => state.profile?.nickname || '',
    bio: (state) => state.profile?.bio || '',
    avatar: (state) => state.profile?.avatar || ''
  },

  actions: {
    async fetch({ silent = false } = {}) {
      if (!silent) this.loading = true
      this.error = null
      try {
        const data = await getProfile()
        this.profile = data || null
        this.loaded = true
        return this.profile
      } catch (e) {
        this.error = e?.serverMessage || e?.message || '加载资料失败'
        throw e
      } finally {
        if (!silent) this.loading = false
      }
    },

    async saveProfile(patch) {
      const next = { ...(this.profile || {}), ...(patch || {}) }
      this.saving = true
      this.error = null
      try {
        const data = await updateProfile(next)
        this.profile = data || next
        return this.profile
      } catch (e) {
        this.error = e?.serverMessage || e?.message || '保存失败'
        throw e
      } finally {
        this.saving = false
      }
    },

    async saveAvatar(avatarUrl) {
      this.saving = true
      this.error = null
      try {
        const updatedAvatar = await updateAvatar(avatarUrl)
        // 后端只返回 avatar string；同步本地 profile.avatar
        this.profile = {
          ...(this.profile || {}),
          avatar: updatedAvatar || avatarUrl
        }
        return this.profile.avatar
      } catch (e) {
        this.error = e?.serverMessage || e?.message || '头像保存失败'
        throw e
      } finally {
        this.saving = false
      }
    },

    reset() {
      this.profile = null
      this.loading = false
      this.saving = false
      this.error = null
      this.loaded = false
    }
  }
})

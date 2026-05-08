import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

vi.mock('../api/users', () => ({
  getProfile: vi.fn(),
  updateProfile: vi.fn(),
  updateAvatar: vi.fn()
}))

import * as usersApi from '../api/users'
import { useProfileStore } from './profile'

describe('profile store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('fetch loads profile and sets loaded', async () => {
    usersApi.getProfile.mockResolvedValueOnce({ id: 1, nickname: 'A', bio: 'b' })
    const store = useProfileStore()
    await store.fetch()
    expect(store.nickname).toBe('A')
    expect(store.bio).toBe('b')
    expect(store.loaded).toBe(true)
  })

  it('fetch captures error', async () => {
    const err = new Error('x')
    err.serverMessage = '后端错'
    usersApi.getProfile.mockRejectedValueOnce(err)
    const store = useProfileStore()
    await expect(store.fetch()).rejects.toThrow('x')
    expect(store.error).toBe('后端错')
  })

  it('saveProfile merges patch with current profile and writes back response', async () => {
    usersApi.getProfile.mockResolvedValueOnce({ id: 1, nickname: 'A', bio: 'b1' })
    usersApi.updateProfile.mockResolvedValueOnce({ id: 1, nickname: 'A2', bio: 'b1' })
    const store = useProfileStore()
    await store.fetch()
    await store.saveProfile({ nickname: 'A2' })
    expect(usersApi.updateProfile).toHaveBeenCalledWith(
      expect.objectContaining({ id: 1, nickname: 'A2', bio: 'b1' })
    )
    expect(store.nickname).toBe('A2')
  })

  it('saveAvatar updates profile.avatar', async () => {
    usersApi.updateAvatar.mockResolvedValueOnce(
      'http://h/api/files/download?objectName=u/x.png'
    )
    const store = useProfileStore()
    store.profile = { nickname: 'A' }
    const avatar = await store.saveAvatar('http://h/api/files/download?objectName=u/x.png')
    expect(avatar).toContain('objectName=')
    expect(store.avatar).toContain('objectName=')
  })

  it('reset clears state', async () => {
    usersApi.getProfile.mockResolvedValueOnce({ id: 1 })
    const store = useProfileStore()
    await store.fetch()
    store.reset()
    expect(store.profile).toBeNull()
    expect(store.loaded).toBe(false)
  })
})

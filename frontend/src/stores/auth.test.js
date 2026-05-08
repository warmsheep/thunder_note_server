import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { setTokenStorage, resetTokenStorage, createMemoryTokenStorage } from '@/api/tokenStorage'

vi.mock('@/api/auth', () => ({
  login: vi.fn(),
  register: vi.fn(),
  refreshToken: vi.fn(),
  logout: vi.fn(),
  changePassword: vi.fn()
}))

import * as authApi from '@/api/auth'
import { useAuthStore } from './auth'

describe('authStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    setTokenStorage(createMemoryTokenStorage())
    if (typeof sessionStorage !== 'undefined') {
      sessionStorage.clear()
    }
    vi.clearAllMocks()
  })

  afterEach(() => {
    resetTokenStorage()
  })

  it('login stores tokens and user info', async () => {
    authApi.login.mockResolvedValue({
      accessToken: 'access-1',
      refreshToken: 'refresh-1',
      tokenType: 'Bearer',
      expiresIn: 3600,
      user: { id: 1, username: 'alice', nickname: 'Alice' }
    })

    const store = useAuthStore()
    await store.login({ username: 'alice', password: 'p' })

    expect(authApi.login).toHaveBeenCalledWith({ username: 'alice', password: 'p' })
    expect(store.user).toMatchObject({ id: 1, username: 'alice' })
    expect(store.isAuthenticated).toBe(true)
    expect(store.displayName).toBe('Alice')
  })

  it('login rejects when response has no accessToken', async () => {
    authApi.login.mockResolvedValue({ accessToken: null })
    const store = useAuthStore()
    await expect(store.login({ username: 'x', password: 'y' })).rejects.toThrow(/accessToken/)
    expect(store.user).toBeNull()
  })

  it('logout calls backend and clears local state', async () => {
    authApi.login.mockResolvedValue({
      accessToken: 'a',
      refreshToken: 'r',
      user: { id: 1, username: 'u' }
    })
    authApi.logout.mockResolvedValue(null)

    const store = useAuthStore()
    await store.login({ username: 'u', password: 'p' })
    expect(store.isAuthenticated).toBe(true)

    await store.logout()

    expect(authApi.logout).toHaveBeenCalledTimes(1)
    expect(store.user).toBeNull()
    expect(store.isAuthenticated).toBe(false)
  })

  it('logout silently only clears local state and does not call backend', async () => {
    authApi.login.mockResolvedValue({
      accessToken: 'a',
      refreshToken: 'r',
      user: { id: 1, username: 'u' }
    })
    const store = useAuthStore()
    await store.login({ username: 'u', password: 'p' })

    await store.logout({ silent: true })

    expect(authApi.logout).not.toHaveBeenCalled()
    expect(store.user).toBeNull()
  })

  it('logout tolerates backend errors and still clears local state', async () => {
    authApi.login.mockResolvedValue({
      accessToken: 'a',
      refreshToken: 'r',
      user: { id: 1, username: 'u' }
    })
    authApi.logout.mockRejectedValue(new Error('backend down'))
    const store = useAuthStore()
    await store.login({ username: 'u', password: 'p' })

    await expect(store.logout()).resolves.toBeUndefined()
    expect(store.user).toBeNull()
  })

  it('refresh updates tokens and user when refresh returns user', async () => {
    authApi.refreshToken.mockResolvedValue({
      accessToken: 'new-a',
      refreshToken: 'new-r',
      user: { id: 2, username: 'bob' }
    })
    const store = useAuthStore()
    await store.refresh('old-refresh')
    expect(authApi.refreshToken).toHaveBeenCalledWith('old-refresh')
    expect(store.user).toMatchObject({ username: 'bob' })
  })

  it('displayName falls back to username when nickname missing', async () => {
    authApi.login.mockResolvedValue({
      accessToken: 'a',
      refreshToken: 'r',
      user: { id: 1, username: 'charlie' }
    })
    const store = useAuthStore()
    await store.login({ username: 'charlie', password: 'p' })
    expect(store.displayName).toBe('charlie')
  })
})

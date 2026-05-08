import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import apiClient, { __resetApiClientForTests } from './client'
import {
  setTokenStorage,
  resetTokenStorage,
  createMemoryTokenStorage
} from './tokenStorage'
import { getProfile, updateProfile, updateAvatar } from './users'

function apiResponse(data) {
  return { code: 0, message: 'OK', data, timestamp: 0 }
}

function installMockAdapter(handler) {
  apiClient.raw.defaults.adapter = handler
}

function uninstallMockAdapter() {
  delete apiClient.raw.defaults.adapter
}

describe('users api wrappers', () => {
  beforeEach(() => {
    setTokenStorage(createMemoryTokenStorage())
    __resetApiClientForTests()
  })

  afterEach(() => {
    uninstallMockAdapter()
    resetTokenStorage()
    __resetApiClientForTests()
  })

  it('getProfile posts to /api/users/profile (POST not GET)', async () => {
    let captured
    installMockAdapter(async (config) => {
      captured = { url: config.url, method: config.method }
      return {
        status: 200,
        data: apiResponse({ id: 1, userId: 9, nickname: 'Alice' }),
        headers: {},
        config
      }
    })
    const profile = await getProfile()
    expect(captured).toEqual({ url: '/api/users/profile', method: 'post' })
    expect(profile.nickname).toBe('Alice')
  })

  it('updateProfile sends full body via PUT', async () => {
    let captured
    installMockAdapter(async (config) => {
      captured = { url: config.url, method: config.method, body: JSON.parse(config.data) }
      return {
        status: 200,
        data: apiResponse({ nickname: 'Bob', bio: 'hi' }),
        headers: {},
        config
      }
    })
    const result = await updateProfile({ nickname: 'Bob', bio: 'hi' })
    expect(captured.url).toBe('/api/users/profile')
    expect(captured.method).toBe('put')
    expect(captured.body).toEqual({ nickname: 'Bob', bio: 'hi' })
    expect(result.nickname).toBe('Bob')
  })

  it('updateAvatar puts { avatar } and returns updated string', async () => {
    let captured
    installMockAdapter(async (config) => {
      captured = { url: config.url, method: config.method, body: JSON.parse(config.data) }
      return {
        status: 200,
        data: apiResponse('http://h/api/files/download?objectName=u1/abc.png'),
        headers: {},
        config
      }
    })
    const url = await updateAvatar('http://h/api/files/download?objectName=u1/abc.png')
    expect(captured.url).toBe('/api/users/avatar')
    expect(captured.method).toBe('put')
    expect(captured.body).toEqual({ avatar: 'http://h/api/files/download?objectName=u1/abc.png' })
    expect(url).toContain('objectName=')
  })

  it('updateAvatar rejects when url is empty', async () => {
    await expect(updateAvatar('')).rejects.toThrow(/avatar/)
  })
})

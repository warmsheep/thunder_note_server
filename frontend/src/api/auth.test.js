import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import apiClient, { __resetApiClientForTests } from './client'
import {
  setTokenStorage,
  resetTokenStorage,
  createMemoryTokenStorage
} from './tokenStorage'
import { changePassword } from './auth'

function apiResponse(data, message = 'OK') {
  return { code: 0, message, data, timestamp: 0 }
}

function installMockAdapter(handler) {
  apiClient.raw.defaults.adapter = handler
}

function uninstallMockAdapter() {
  delete apiClient.raw.defaults.adapter
}

describe('auth.changePassword wrapper', () => {
  beforeEach(() => {
    setTokenStorage(createMemoryTokenStorage())
    __resetApiClientForTests()
  })

  afterEach(() => {
    uninstallMockAdapter()
    resetTokenStorage()
    __resetApiClientForTests()
  })

  it('puts /api/auth/password with currentPassword + newPassword body', async () => {
    let captured
    installMockAdapter(async (config) => {
      captured = {
        url: config.url,
        method: config.method,
        body: JSON.parse(config.data)
      }
      return { status: 200, data: apiResponse(null, 'Password changed'), headers: {}, config }
    })
    await changePassword({ currentPassword: 'old123', newPassword: 'newer123' })
    expect(captured).toEqual({
      url: '/api/auth/password',
      method: 'put',
      body: { currentPassword: 'old123', newPassword: 'newer123' }
    })
  })

  it('rejects when currentPassword is missing', async () => {
    await expect(
      changePassword({ currentPassword: '', newPassword: 'abc123' })
    ).rejects.toThrow(/currentPassword/)
  })

  it('rejects when newPassword is missing', async () => {
    await expect(
      changePassword({ currentPassword: 'old', newPassword: '' })
    ).rejects.toThrow(/newPassword/)
  })

  it('propagates server-side error message', async () => {
    installMockAdapter(async (config) => ({
      status: 200,
      data: { code: 400, message: 'Current password is incorrect', data: null, timestamp: 0 },
      headers: {},
      config
    }))
    await expect(
      changePassword({ currentPassword: 'wrong', newPassword: 'newer123' })
    ).rejects.toMatchObject({ serverMessage: 'Current password is incorrect' })
  })
})

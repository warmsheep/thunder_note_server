import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import apiClient, { configureApiClient, __resetApiClientForTests } from './client'
import { setTokenStorage, resetTokenStorage, createMemoryTokenStorage } from './tokenStorage'

// 自定义 axios adapter，让测试不真正发网络请求。
// 调用方通过 scenario 指定每个 URL 的响应行为。
function installMockAdapter(scenario) {
  apiClient.raw.defaults.adapter = async (config) => {
    const handler = scenario[config.url]
    if (!handler) {
      return {
        data: null,
        status: 404,
        statusText: 'Not Found',
        headers: {},
        config
      }
    }
    return handler(config)
  }
}

function uninstallMockAdapter() {
  delete apiClient.raw.defaults.adapter
}

function apiResponse(data) {
  return { code: 0, message: 'OK', data, timestamp: 0 }
}

function apiError(code, message) {
  return { code, message, data: null, timestamp: 0 }
}

describe('apiClient', () => {
  beforeEach(() => {
    setTokenStorage(createMemoryTokenStorage())
    __resetApiClientForTests()
  })

  afterEach(() => {
    uninstallMockAdapter()
    resetTokenStorage()
    __resetApiClientForTests()
  })

  it('unwraps successful ApiResponse and returns data field', async () => {
    installMockAdapter({
      '/api/echo': (config) => ({
        status: 200,
        data: apiResponse({ hello: 'world' }),
        headers: {},
        config
      })
    })
    const data = await apiClient.get('/api/echo')
    expect(data).toEqual({ hello: 'world' })
  })

  it('throws business error when ApiResponse code is non-zero', async () => {
    installMockAdapter({
      '/api/fail': (config) => ({
        status: 200,
        data: apiError(40001, '参数错误'),
        headers: {},
        config
      })
    })
    await expect(apiClient.get('/api/fail')).rejects.toMatchObject({
      message: '参数错误',
      code: 40001
    })
  })

  it('returns raw body when response is not an ApiResponse envelope', async () => {
    installMockAdapter({
      '/actuator/health': (config) => ({
        status: 200,
        data: { status: 'UP' },
        headers: {},
        config
      })
    })
    const data = await apiClient.get('/actuator/health')
    expect(data).toEqual({ status: 'UP' })
  })

  it('injects Bearer token from storage when available', async () => {
    setTokenStorage(createMemoryTokenStorage({ accessToken: 'token-a' }))
    let observed = null
    installMockAdapter({
      '/api/me': (config) => {
        observed = config.headers.Authorization
        return {
          status: 200,
          data: apiResponse({ id: 1 }),
          headers: {},
          config
        }
      }
    })
    await apiClient.get('/api/me')
    expect(observed).toBe('Bearer token-a')
  })

  it('does not inject Authorization header when skipAuth is true', async () => {
    setTokenStorage(createMemoryTokenStorage({ accessToken: 'token-a' }))
    let observed = 'untouched'
    installMockAdapter({
      '/api/auth/login': (config) => {
        observed = config.headers.Authorization
        return {
          status: 200,
          data: apiResponse({ accessToken: 'x', refreshToken: 'y' }),
          headers: {},
          config
        }
      }
    })
    await apiClient.post('/api/auth/login', { username: 'u', password: 'p' }, { skipAuth: true })
    expect(observed).toBeUndefined()
  })

  it('on 401 attempts refresh and replays the original request', async () => {
    const storage = createMemoryTokenStorage({
      accessToken: 'expired-access',
      refreshToken: 'refresh-1'
    })
    setTokenStorage(storage)

    const refresh = vi.fn().mockResolvedValue({
      accessToken: 'new-access',
      refreshToken: 'new-refresh'
    })
    configureApiClient({ refresh })

    let callCount = 0
    installMockAdapter({
      '/api/secure': (config) => {
        callCount += 1
        if (callCount === 1) {
          const error = new Error('Unauthorized')
          error.response = { status: 401, data: null, headers: {}, config }
          error.config = config
          return Promise.reject(error)
        }
        return {
          status: 200,
          data: apiResponse({ ok: true }),
          headers: {},
          config
        }
      }
    })

    const data = await apiClient.get('/api/secure')
    expect(data).toEqual({ ok: true })
    expect(refresh).toHaveBeenCalledWith('refresh-1')
    expect(storage.getAccessToken()).toBe('new-access')
    expect(storage.getRefreshToken()).toBe('new-refresh')
    expect(callCount).toBe(2)
  })

  it('clears tokens and notifies onUnauthorized when refresh itself fails', async () => {
    const storage = createMemoryTokenStorage({
      accessToken: 'expired-access',
      refreshToken: 'refresh-1'
    })
    setTokenStorage(storage)

    const refresh = vi.fn().mockRejectedValue(new Error('refresh rejected'))
    const onUnauthorized = vi.fn()
    configureApiClient({ refresh, onUnauthorized })

    installMockAdapter({
      '/api/secure': (config) => {
        const error = new Error('Unauthorized')
        error.response = { status: 401, data: null, headers: {}, config }
        error.config = config
        return Promise.reject(error)
      }
    })

    await expect(apiClient.get('/api/secure')).rejects.toBeTruthy()
    expect(storage.getAccessToken()).toBeNull()
    expect(storage.getRefreshToken()).toBeNull()
    expect(onUnauthorized).toHaveBeenCalledTimes(1)
  })

  it('clears tokens and notifies onUnauthorized when there is no refresh token', async () => {
    const storage = createMemoryTokenStorage({ accessToken: 'expired-access' })
    setTokenStorage(storage)

    const refresh = vi.fn()
    const onUnauthorized = vi.fn()
    configureApiClient({ refresh, onUnauthorized })

    installMockAdapter({
      '/api/secure': (config) => {
        const error = new Error('Unauthorized')
        error.response = { status: 401, data: null, headers: {}, config }
        error.config = config
        return Promise.reject(error)
      }
    })

    await expect(apiClient.get('/api/secure')).rejects.toBeTruthy()
    expect(refresh).not.toHaveBeenCalled()
    expect(onUnauthorized).toHaveBeenCalledTimes(1)
    expect(storage.getAccessToken()).toBeNull()
  })

  it('does not retry a request that already went through refresh once', async () => {
    const storage = createMemoryTokenStorage({
      accessToken: 'a',
      refreshToken: 'r'
    })
    setTokenStorage(storage)
    const refresh = vi.fn().mockResolvedValue({
      accessToken: 'a2',
      refreshToken: 'r2'
    })
    const onUnauthorized = vi.fn()
    configureApiClient({ refresh, onUnauthorized })

    let hits = 0
    installMockAdapter({
      '/api/still-401': (config) => {
        hits += 1
        const error = new Error('Unauthorized')
        error.response = { status: 401, data: null, headers: {}, config }
        error.config = config
        return Promise.reject(error)
      }
    })

    await expect(apiClient.get('/api/still-401')).rejects.toBeTruthy()
    // 第一次原请求 + 一次 refreshed retry = 2 次，不允许无限循环
    expect(hits).toBe(2)
    expect(refresh).toHaveBeenCalledTimes(1)
  })
})

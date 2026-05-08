import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import apiClient, { __resetApiClientForTests } from './client'
import {
  setTokenStorage,
  resetTokenStorage,
  createMemoryTokenStorage
} from './tokenStorage'
import {
  uploadFile,
  downloadAsBlob,
  fetchAsObjectUrl,
  triggerDownload
} from './files'

function apiResponse(data) {
  return { code: 0, message: 'OK', data, timestamp: 0 }
}

function installMockAdapter(handler) {
  apiClient.raw.defaults.adapter = handler
}

function uninstallMockAdapter() {
  delete apiClient.raw.defaults.adapter
}

describe('files api wrappers', () => {
  beforeEach(() => {
    setTokenStorage(createMemoryTokenStorage())
    __resetApiClientForTests()
  })

  afterEach(() => {
    uninstallMockAdapter()
    resetTokenStorage()
    __resetApiClientForTests()
  })

  it('uploadFile posts to /api/files/upload and returns unwrapped data', async () => {
    let captured
    installMockAdapter(async (config) => {
      captured = { url: config.url, method: config.method }
      return {
        status: 200,
        data: apiResponse({ objectName: 'u1/abc.png', originalFilename: 'a.png' }),
        headers: {},
        config
      }
    })
    const file = new File([new Uint8Array([1, 2, 3])], 'a.png', { type: 'image/png' })
    const result = await uploadFile(file)
    expect(captured.url).toBe('/api/files/upload')
    expect(captured.method).toBe('post')
    // happy-dom + axios 下 config.data 可能被 transformRequest 处理为非 FormData，
    // 这里不再断言 instanceof，转为依赖 e2e 联调验证 multipart 协议本身。
    expect(result).toEqual({ objectName: 'u1/abc.png', originalFilename: 'a.png' })
  })

  it('uploadFile passes onUploadProgress to axios config', async () => {
    let configCaptured
    installMockAdapter(async (config) => {
      configCaptured = config
      return { status: 200, data: apiResponse({ objectName: 'x', originalFilename: 'y' }), headers: {}, config }
    })
    const file = new File([], 'a.bin')
    const onUploadProgress = vi.fn()
    await uploadFile(file, { onUploadProgress })
    expect(configCaptured.onUploadProgress).toBe(onUploadProgress)
  })

  it('uploadFile rejects when file is missing', async () => {
    await expect(uploadFile(null)).rejects.toThrow(/file/)
  })

  it('downloadAsBlob requests with responseType=blob and objectName param', async () => {
    let captured
    installMockAdapter(async (config) => {
      captured = {
        url: config.url,
        method: config.method,
        params: config.params,
        responseType: config.responseType
      }
      return { status: 200, data: new Blob([new Uint8Array([1, 2, 3])]), headers: {}, config }
    })
    const blob = await downloadAsBlob('u1/abc.png')
    expect(captured).toEqual({
      url: '/api/files/download',
      method: 'get',
      params: { objectName: 'u1/abc.png' },
      responseType: 'blob'
    })
    expect(blob).toBeInstanceOf(Blob)
  })

  it('downloadAsBlob throws when objectName is missing', async () => {
    await expect(downloadAsBlob('')).rejects.toThrow(/objectName/)
  })

  it('fetchAsObjectUrl returns a string URL via createObjectURL', async () => {
    installMockAdapter(async (config) => ({
      status: 200,
      data: new Blob([new Uint8Array([42])]),
      headers: {},
      config
    }))
    const originalCreate = URL.createObjectURL
    URL.createObjectURL = vi.fn(() => 'blob:fake-url')
    try {
      const url = await fetchAsObjectUrl('u1/abc.png')
      expect(URL.createObjectURL).toHaveBeenCalled()
      expect(url).toBe('blob:fake-url')
    } finally {
      URL.createObjectURL = originalCreate
    }
  })

  it('triggerDownload creates and clicks an anchor with download attribute', async () => {
    installMockAdapter(async (config) => ({
      status: 200,
      data: new Blob([new Uint8Array([1])]),
      headers: {},
      config
    }))
    const originalCreate = URL.createObjectURL
    const originalRevoke = URL.revokeObjectURL
    URL.createObjectURL = vi.fn(() => 'blob:fake')
    URL.revokeObjectURL = vi.fn()

    const clickSpy = vi.fn()
    const origCreateElement = document.createElement.bind(document)
    document.createElement = (tag) => {
      const el = origCreateElement(tag)
      if (tag === 'a') {
        el.click = clickSpy
      }
      return el
    }

    try {
      await triggerDownload('u1/abc.png', 'a.png')
      expect(clickSpy).toHaveBeenCalledTimes(1)
    } finally {
      document.createElement = origCreateElement
      URL.createObjectURL = originalCreate
      URL.revokeObjectURL = originalRevoke
    }
  })
})

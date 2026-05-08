import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import apiClient, { __resetApiClientForTests } from './client'
import {
  setTokenStorage,
  resetTokenStorage,
  createMemoryTokenStorage
} from './tokenStorage'
import {
  listContacts,
  searchContacts,
  listFriendRequests,
  countFriendRequests,
  sendFriendRequest,
  acceptFriendRequest,
  rejectFriendRequest,
  cancelFriendRequest,
  deleteContact
} from './users'

function apiResponse(data) {
  return { code: 0, message: 'OK', data, timestamp: 0 }
}

function installMockAdapter(handler) {
  apiClient.raw.defaults.adapter = handler
}

function uninstallMockAdapter() {
  delete apiClient.raw.defaults.adapter
}

describe('contacts api wrappers', () => {
  beforeEach(() => {
    setTokenStorage(createMemoryTokenStorage())
    __resetApiClientForTests()
  })

  afterEach(() => {
    uninstallMockAdapter()
    resetTokenStorage()
    __resetApiClientForTests()
  })

  it('listContacts uses GET /api/users/contacts', async () => {
    let captured
    installMockAdapter(async (config) => {
      captured = { url: config.url, method: config.method }
      return { status: 200, data: apiResponse([{ userId: 1, username: 'a' }]), headers: {}, config }
    })
    const data = await listContacts()
    expect(captured).toEqual({ url: '/api/users/contacts', method: 'get' })
    expect(data[0].userId).toBe(1)
  })

  it('searchContacts sends keyword as query param', async () => {
    let captured
    installMockAdapter(async (config) => {
      captured = { url: config.url, method: config.method, params: config.params }
      return { status: 200, data: apiResponse([]), headers: {}, config }
    })
    await searchContacts('hello')
    expect(captured).toEqual({
      url: '/api/users/contacts/search',
      method: 'get',
      params: { keyword: 'hello' }
    })
  })

  it('searchContacts coerces null/undefined to empty string', async () => {
    let captured
    installMockAdapter(async (config) => {
      captured = config.params
      return { status: 200, data: apiResponse([]), headers: {}, config }
    })
    await searchContacts(null)
    expect(captured.keyword).toBe('')
  })

  it('listFriendRequests uses GET /requests', async () => {
    let captured
    installMockAdapter(async (config) => {
      captured = { url: config.url, method: config.method }
      return { status: 200, data: apiResponse([]), headers: {}, config }
    })
    await listFriendRequests()
    expect(captured).toEqual({ url: '/api/users/contacts/requests', method: 'get' })
  })

  it('countFriendRequests returns numeric data', async () => {
    installMockAdapter(async (config) => ({
      status: 200,
      data: apiResponse(7),
      headers: {},
      config
    }))
    const n = await countFriendRequests()
    expect(n).toBe(7)
  })

  it('sendFriendRequest posts targetUserId in body', async () => {
    let captured
    installMockAdapter(async (config) => {
      captured = { url: config.url, method: config.method, body: JSON.parse(config.data) }
      return { status: 200, data: apiResponse(null), headers: {}, config }
    })
    await sendFriendRequest(42)
    expect(captured).toEqual({
      url: '/api/users/contacts/request',
      method: 'post',
      body: { targetUserId: 42 }
    })
  })

  it('sendFriendRequest rejects when targetUserId is missing', async () => {
    await expect(sendFriendRequest(null)).rejects.toThrow(/targetUserId/)
  })

  it('acceptFriendRequest posts requestId in body', async () => {
    let captured
    installMockAdapter(async (config) => {
      captured = { url: config.url, method: config.method, body: JSON.parse(config.data) }
      return { status: 200, data: apiResponse(null), headers: {}, config }
    })
    await acceptFriendRequest(9)
    expect(captured).toEqual({
      url: '/api/users/contacts/request/accept',
      method: 'post',
      body: { requestId: 9 }
    })
  })

  it('rejectFriendRequest posts requestId in body', async () => {
    let captured
    installMockAdapter(async (config) => {
      captured = { url: config.url, method: config.method, body: JSON.parse(config.data) }
      return { status: 200, data: apiResponse(null), headers: {}, config }
    })
    await rejectFriendRequest(11)
    expect(captured.url).toBe('/api/users/contacts/request/reject')
    expect(captured.body).toEqual({ requestId: 11 })
  })

  it('cancelFriendRequest deletes /request/:id', async () => {
    let captured
    installMockAdapter(async (config) => {
      captured = { url: config.url, method: config.method }
      return { status: 200, data: apiResponse(null), headers: {}, config }
    })
    await cancelFriendRequest(33)
    expect(captured).toEqual({ url: '/api/users/contacts/request/33', method: 'delete' })
  })

  it('deleteContact deletes /:contactUserId', async () => {
    let captured
    installMockAdapter(async (config) => {
      captured = { url: config.url, method: config.method }
      return { status: 200, data: apiResponse(null), headers: {}, config }
    })
    await deleteContact(55)
    expect(captured).toEqual({ url: '/api/users/contacts/55', method: 'delete' })
  })

  it('rejects when ids are missing', async () => {
    await expect(acceptFriendRequest(null)).rejects.toThrow(/requestId/)
    await expect(rejectFriendRequest(undefined)).rejects.toThrow(/requestId/)
    await expect(cancelFriendRequest(null)).rejects.toThrow(/requestId/)
    await expect(deleteContact(null)).rejects.toThrow(/contactUserId/)
  })
})

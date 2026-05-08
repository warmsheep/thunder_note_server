import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

vi.mock('../api/users', () => ({
  listContacts: vi.fn(),
  searchContacts: vi.fn(),
  listFriendRequests: vi.fn(),
  countFriendRequests: vi.fn(),
  sendFriendRequest: vi.fn(),
  acceptFriendRequest: vi.fn(),
  rejectFriendRequest: vi.fn(),
  cancelFriendRequest: vi.fn(),
  deleteContact: vi.fn()
}))

import * as usersApi from '../api/users'
import { useContactsStore } from './contacts'

function contact(userId, status = 'FRIEND', name = `u${userId}`) {
  return { userId, username: name, nickname: name, avatar: null, relationStatus: status, latestMessage: null }
}

function request(requestId, userId = requestId * 10) {
  return { requestId, userId, username: `u${userId}`, nickname: `u${userId}`, avatar: null }
}

describe('contacts store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('fetchContacts loads list', async () => {
    usersApi.listContacts.mockResolvedValueOnce([contact(1), contact(2, 'PENDING_SENT')])
    const store = useContactsStore()
    await store.fetchContacts()
    expect(store.contactsCount).toBe(2)
    expect(store.contactsLoaded).toBe(true)
    expect(store.findContactById(2)?.relationStatus).toBe('PENDING_SENT')
  })

  it('fetchContacts captures error', async () => {
    const err = new Error('boom')
    err.serverMessage = '后端错误'
    usersApi.listContacts.mockRejectedValueOnce(err)
    const store = useContactsStore()
    await expect(store.fetchContacts()).rejects.toThrow('boom')
    expect(store.error).toBe('后端错误')
  })

  it('fetchPendingCount sets numeric count and stays silent on error', async () => {
    usersApi.countFriendRequests.mockResolvedValueOnce(3)
    const store = useContactsStore()
    await store.fetchPendingCount()
    expect(store.pendingCount).toBe(3)

    usersApi.countFriendRequests.mockRejectedValueOnce(new Error('x'))
    const before = store.pendingCount
    await store.fetchPendingCount()
    expect(store.pendingCount).toBe(before)
  })

  it('search trims keyword and stores results', async () => {
    usersApi.searchContacts.mockResolvedValueOnce([
      { userId: 9, username: 'bob', relationStatus: 'NONE' }
    ])
    const store = useContactsStore()
    await store.search('  bob  ')
    expect(usersApi.searchContacts).toHaveBeenCalledWith('bob')
    expect(store.searchResults[0].userId).toBe(9)
    expect(store.searchHasRun).toBe(true)
  })

  it('empty/whitespace search clears results without calling api', async () => {
    const store = useContactsStore()
    store.searchResults = [{ userId: 1 }]
    await store.search('   ')
    expect(usersApi.searchContacts).not.toHaveBeenCalled()
    expect(store.searchResults).toEqual([])
    expect(store.searchHasRun).toBe(false)
  })

  it('search runId guards stale responses', async () => {
    let resolveFirst
    const first = new Promise((r) => { resolveFirst = r })
    usersApi.searchContacts
      .mockReturnValueOnce(first)
      .mockResolvedValueOnce([{ userId: 99, username: 'newer' }])
    const store = useContactsStore()
    const p1 = store.search('old')
    const p2 = store.search('new')
    await p2
    resolveFirst([{ userId: 1, username: 'old' }])
    await p1
    expect(store.searchKeyword).toBe('new')
    expect(store.searchResults[0].userId).toBe(99)
  })

  it('acceptRequest removes from list and refreshes count + contacts', async () => {
    usersApi.listFriendRequests.mockResolvedValueOnce([request(7), request(8)])
    usersApi.acceptFriendRequest.mockResolvedValueOnce(null)
    usersApi.countFriendRequests.mockResolvedValueOnce(1)
    usersApi.listContacts.mockResolvedValueOnce([contact(70)])
    const store = useContactsStore()
    await store.fetchFriendRequests()
    await store.acceptRequest(7)
    expect(store.friendRequests.map((r) => r.requestId)).toEqual([8])
    expect(usersApi.countFriendRequests).toHaveBeenCalled()
    expect(usersApi.listContacts).toHaveBeenCalled()
  })

  it('rejectRequest removes from list and refreshes count', async () => {
    usersApi.listFriendRequests.mockResolvedValueOnce([request(7), request(8)])
    usersApi.rejectFriendRequest.mockResolvedValueOnce(null)
    usersApi.countFriendRequests.mockResolvedValueOnce(1)
    const store = useContactsStore()
    await store.fetchFriendRequests()
    await store.rejectRequest(7)
    expect(store.friendRequests.map((r) => r.requestId)).toEqual([8])
  })

  it('cancelRequest refreshes contacts (PENDING_SENT entries should disappear)', async () => {
    usersApi.cancelFriendRequest.mockResolvedValueOnce(null)
    usersApi.listContacts.mockResolvedValueOnce([])
    const store = useContactsStore()
    await store.cancelRequest(99)
    expect(usersApi.cancelFriendRequest).toHaveBeenCalledWith(99)
    expect(usersApi.listContacts).toHaveBeenCalled()
  })

  it('removeContact deletes from local list', async () => {
    usersApi.listContacts.mockResolvedValueOnce([contact(1), contact(2)])
    usersApi.deleteContact.mockResolvedValueOnce(null)
    const store = useContactsStore()
    await store.fetchContacts()
    await store.removeContact(1)
    expect(store.contacts.map((c) => c.userId)).toEqual([2])
  })

  it('reset clears all state', async () => {
    usersApi.listContacts.mockResolvedValueOnce([contact(1)])
    const store = useContactsStore()
    await store.fetchContacts()
    store.reset()
    expect(store.contacts).toEqual([])
    expect(store.contactsLoaded).toBe(false)
    expect(store.pendingCount).toBe(0)
  })
})

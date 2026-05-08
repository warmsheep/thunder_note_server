import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import apiClient, { __resetApiClientForTests } from './client'
import {
  setTokenStorage,
  resetTokenStorage,
  createMemoryTokenStorage
} from './tokenStorage'
import {
  listFlashNotes,
  createFlashNote,
  updateFlashNote,
  setFlashNotePinned,
  setFlashNoteHidden,
  deleteFlashNote
} from './flashNotes'

function apiResponse(data) {
  return { code: 0, message: 'OK', data, timestamp: 0 }
}

function installMockAdapter(handler) {
  apiClient.raw.defaults.adapter = handler
}

function uninstallMockAdapter() {
  delete apiClient.raw.defaults.adapter
}

describe('flashNotes api wrappers', () => {
  beforeEach(() => {
    setTokenStorage(createMemoryTokenStorage())
    __resetApiClientForTests()
  })

  afterEach(() => {
    uninstallMockAdapter()
    resetTokenStorage()
    __resetApiClientForTests()
  })

  it('listFlashNotes posts to /api/flash-notes/list and returns data array', async () => {
    let captured
    installMockAdapter(async (config) => {
      captured = { url: config.url, method: config.method }
      return { status: 200, data: apiResponse([{ id: 1, title: 'a' }]), headers: {}, config }
    })
    const data = await listFlashNotes()
    expect(captured).toEqual({ url: '/api/flash-notes/list', method: 'post' })
    expect(data).toEqual([{ id: 1, title: 'a' }])
  })

  it('createFlashNote posts payload to /api/flash-notes', async () => {
    let captured
    installMockAdapter(async (config) => {
      captured = { url: config.url, method: config.method, body: config.data }
      return { status: 200, data: apiResponse({ id: 9, title: 'new' }), headers: {}, config }
    })
    const created = await createFlashNote({ title: 'new', icon: '📝' })
    expect(captured.url).toBe('/api/flash-notes')
    expect(captured.method).toBe('post')
    expect(JSON.parse(captured.body)).toEqual({ title: 'new', icon: '📝' })
    expect(created).toEqual({ id: 9, title: 'new' })
  })

  it('updateFlashNote puts to /api/flash-notes/:id', async () => {
    let captured
    installMockAdapter(async (config) => {
      captured = { url: config.url, method: config.method, body: config.data }
      return { status: 200, data: apiResponse({ id: 7, title: 'patched' }), headers: {}, config }
    })
    await updateFlashNote(7, { title: 'patched' })
    expect(captured).toEqual({
      url: '/api/flash-notes/7',
      method: 'put',
      body: JSON.stringify({ title: 'patched' })
    })
  })

  it('updateFlashNote rejects when id is missing', async () => {
    await expect(updateFlashNote(null, {})).rejects.toThrow(/id is required/)
  })

  it('setFlashNotePinned sends value=true / value=false as query param', async () => {
    const captures = []
    installMockAdapter(async (config) => {
      captures.push({ url: config.url, method: config.method, params: config.params })
      return { status: 200, data: apiResponse({ id: 3, pinned: true }), headers: {}, config }
    })
    await setFlashNotePinned(3, true)
    await setFlashNotePinned(3, false)
    expect(captures[0]).toEqual({ url: '/api/flash-notes/3/pin', method: 'put', params: { value: 'true' } })
    expect(captures[1]).toEqual({ url: '/api/flash-notes/3/pin', method: 'put', params: { value: 'false' } })
  })

  it('setFlashNoteHidden sends value as query param', async () => {
    let captured
    installMockAdapter(async (config) => {
      captured = { url: config.url, method: config.method, params: config.params }
      return { status: 200, data: apiResponse({ id: 4, hidden: true }), headers: {}, config }
    })
    await setFlashNoteHidden(4, true)
    expect(captured).toEqual({ url: '/api/flash-notes/4/hide', method: 'put', params: { value: 'true' } })
  })

  it('deleteFlashNote sends DELETE to /api/flash-notes/:id', async () => {
    let captured
    installMockAdapter(async (config) => {
      captured = { url: config.url, method: config.method }
      return { status: 200, data: apiResponse(null), headers: {}, config }
    })
    await deleteFlashNote(11)
    expect(captured).toEqual({ url: '/api/flash-notes/11', method: 'delete' })
  })

  it('deleteFlashNote rejects when id is missing', async () => {
    await expect(deleteFlashNote(undefined)).rejects.toThrow(/id is required/)
  })
})

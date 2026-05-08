import apiClient from './client'

// D1-W5 闪记 API 封装，对齐真实 controller `/api/flash-notes`。
// 所有方法返回 Promise<解包后的 data>，业务异常会被 apiClient 抛出 Error。

export function listFlashNotes() {
  // 真实 controller 是 POST /api/flash-notes/list（无 body），不是 GET。
  return apiClient.post('/api/flash-notes/list')
}

export function createFlashNote(payload) {
  // payload: { title (必填), icon?, content?, tags?, pinned?, hidden? }
  return apiClient.post('/api/flash-notes', payload)
}

export function updateFlashNote(id, payload) {
  if (id == null) {
    return Promise.reject(new Error('id is required'))
  }
  return apiClient.put(`/api/flash-notes/${id}`, payload || {})
}

export function setFlashNotePinned(id, pinned) {
  if (id == null) {
    return Promise.reject(new Error('id is required'))
  }
  return apiClient.put(`/api/flash-notes/${id}/pin`, null, {
    params: { value: pinned ? 'true' : 'false' }
  })
}

export function setFlashNoteHidden(id, hidden) {
  if (id == null) {
    return Promise.reject(new Error('id is required'))
  }
  return apiClient.put(`/api/flash-notes/${id}/hide`, null, {
    params: { value: hidden ? 'true' : 'false' }
  })
}

export function deleteFlashNote(id) {
  if (id == null) {
    return Promise.reject(new Error('id is required'))
  }
  return apiClient.delete(`/api/flash-notes/${id}`)
}

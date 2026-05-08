import { defineStore } from 'pinia'
import {
  listMessages,
  sendMessage as sendMessageApi,
  deleteMessage as deleteMessageApi,
  deleteMessagesBatch as deleteMessagesBatchApi,
  clearInbox as clearInboxApi,
  mergeMessages as mergeMessagesApi
} from '../api/messages'
import {
  buildInitialMessages,
  mergeOlderRecords,
  replaceOptimisticByClientId,
  createOptimisticMessage,
  newClientRequestId,
  isInboxFlashNoteId
} from '../utils/messageHelpers'

// D1-W6 当前会话 store。一次只承载一个会话（切换会话时 reset 后重新拉取）。
// - messages：升序数组（最新在末尾），由首页 + 历史页合并
// - 分页：page 从 1 开始；判定 hasMore 用 current < pages
// - send：optimistic 先插一条 pending，server 返回后用 clientRequestId 替换
// - 多选删除：selectMode + selectedIds Set
const DEFAULT_PAGE_SIZE = 30

export const useChatStore = defineStore('chat', {
  state: () => ({
    flashNoteId: null,
    messages: [],
    loading: false,
    sending: false,
    loadingMore: false,
    error: null,
    page: 0,
    pageSize: DEFAULT_PAGE_SIZE,
    pages: 0,
    total: 0,
    selectMode: false,
    selectedIds: new Set()
  }),

  getters: {
    hasMore: (state) => state.page > 0 && state.page < state.pages,
    pendingCount: (state) => state.messages.filter((m) => m && m.__status === 'pending').length,
    failedCount: (state) => state.messages.filter((m) => m && m.__status === 'failed').length
  },

  actions: {
    async openConversation(flashNoteId) {
      this.reset()
      this.flashNoteId = Number(flashNoteId)
      this.loading = true
      try {
        const page = await listMessages({
          flashNoteId: this.flashNoteId,
          page: 1,
          limit: this.pageSize
        })
        this.messages = buildInitialMessages(page && page.records)
        this.page = page && page.current ? Number(page.current) : 1
        this.pages = page && page.pages ? Number(page.pages) : 1
        this.total = page && page.total ? Number(page.total) : 0
      } catch (e) {
        this.error = e?.serverMessage || e?.message || '加载消息失败'
        throw e
      } finally {
        this.loading = false
      }
    },

    async loadMore() {
      if (this.loadingMore || !this.hasMore || this.flashNoteId == null) return
      this.loadingMore = true
      try {
        const nextPage = this.page + 1
        const page = await listMessages({
          flashNoteId: this.flashNoteId,
          page: nextPage,
          limit: this.pageSize
        })
        this.messages = mergeOlderRecords(this.messages, page && page.records)
        this.page = page && page.current ? Number(page.current) : nextPage
        this.pages = page && page.pages ? Number(page.pages) : this.pages
        this.total = page && page.total ? Number(page.total) : this.total
      } catch (e) {
        this.error = e?.serverMessage || e?.message || '加载历史失败'
        throw e
      } finally {
        this.loadingMore = false
      }
    },

    async send({ content, currentUserId, media = null }) {
      const trimmed = (content || '').trim()
      const hasMedia = Boolean(media && media.mediaType)
      if (!trimmed && !hasMedia) {
        throw new Error('消息内容不能为空')
      }
      if (this.flashNoteId == null) {
        throw new Error('未选择会话')
      }
      const cr = newClientRequestId()
      const optimistic = createOptimisticMessage({
        flashNoteId: this.flashNoteId,
        content: trimmed,
        clientRequestId: cr,
        currentUserId,
        mediaType: hasMedia ? media.mediaType : null,
        mediaUrl: hasMedia ? media.mediaUrl : null,
        fileName: hasMedia ? media.fileName : null,
        fileSize: hasMedia ? media.fileSize : null,
        mediaDuration: hasMedia ? media.mediaDuration : null,
        thumbnailUrl: hasMedia ? media.thumbnailUrl : null
      })
      this.messages = [...this.messages, optimistic]
      this.sending = true
      try {
        const serverMessage = await sendMessageApi({
          flashNoteId: this.flashNoteId,
          content: trimmed,
          clientRequestId: cr,
          role: 'user',
          mediaType: hasMedia ? media.mediaType : null,
          mediaUrl: hasMedia ? media.mediaUrl : null,
          fileName: hasMedia ? media.fileName : null,
          fileSize: hasMedia ? media.fileSize : null,
          mediaDuration: hasMedia ? media.mediaDuration : null,
          thumbnailUrl: hasMedia ? media.thumbnailUrl : null
        })
        this.messages = replaceOptimisticByClientId(this.messages, serverMessage)
        this.total += 1
        return serverMessage
      } catch (e) {
        // 标记失败，保留 optimistic 消息让用户看到状态
        this.markFailed(cr, e)
        throw e
      } finally {
        this.sending = false
      }
    },

    markFailed(clientRequestId, error) {
      const idx = this.messages.findIndex(
        (m) => m && m.__local && m.clientRequestId === clientRequestId
      )
      if (idx < 0) return
      const next = this.messages.slice()
      next[idx] = {
        ...next[idx],
        __status: 'failed',
        __error: error?.serverMessage || error?.message || '发送失败'
      }
      this.messages = next
    },

    async retryFailed(clientRequestId, currentUserId) {
      const idx = this.messages.findIndex(
        (m) => m && m.__local && m.clientRequestId === clientRequestId
      )
      if (idx < 0) return
      const target = this.messages[idx]
      // 重置为 pending，再走一次发送
      const next = this.messages.slice()
      next[idx] = { ...target, __status: 'pending', __error: null }
      this.messages = next
      this.sending = true
      try {
        const serverMessage = await sendMessageApi({
          flashNoteId: this.flashNoteId,
          content: target.content,
          clientRequestId,
          role: target.role || 'user'
        })
        this.messages = replaceOptimisticByClientId(this.messages, serverMessage)
        this.total += 1
        return serverMessage
      } catch (e) {
        this.markFailed(clientRequestId, e)
        throw e
      } finally {
        this.sending = false
      }
      void currentUserId
    },

    async remove(id) {
      if (id == null) return
      try {
        await deleteMessageApi(id)
        this.messages = this.messages.filter((m) => m && m.id !== id)
        this.selectedIds.delete(id)
        this.total = Math.max(0, this.total - 1)
      } catch (e) {
        this.error = e?.serverMessage || e?.message || '删除失败'
        throw e
      }
    },

    async batchRemove() {
      const ids = Array.from(this.selectedIds).filter((v) => v != null)
      if (ids.length === 0) return
      try {
        await deleteMessagesBatchApi(ids)
        this.messages = this.messages.filter((m) => !(m && ids.includes(m.id)))
        this.total = Math.max(0, this.total - ids.length)
        this.selectedIds = new Set()
        this.selectMode = false
      } catch (e) {
        this.error = e?.serverMessage || e?.message || '批量删除失败'
        throw e
      }
    },

    // D1-W17-01 合并多选消息为卡片
    // 调用方传入 title；返回新生成的卡片消息（已追加到 messages 末尾）
    // 注意：后端 merge 只在末尾**新增一条** COMPOSITE 消息，原消息不删除
    async mergeSelected({ title }) {
      const ids = Array.from(this.selectedIds).filter((v) => v != null)
      if (ids.length === 0) {
        throw new Error('未选择任何消息')
      }
      if (this.flashNoteId == null) {
        throw new Error('当前会话未就绪')
      }
      try {
        const cardMessage = await mergeMessagesApi({
          title,
          messageIds: ids,
          flashNoteId: this.flashNoteId
        })
        if (cardMessage && cardMessage.id != null) {
          this.messages = [...this.messages, cardMessage]
          this.total = this.total + 1
        }
        this.selectedIds = new Set()
        this.selectMode = false
        return cardMessage
      } catch (e) {
        this.error = e?.serverMessage || e?.message || '合并失败'
        throw e
      }
    },

    // D1-W17-03 转发多选消息到目标闪记
    // 后端无原生转发接口，按原顺序循环 sendMessage 到目标 flashNoteId
    // - 仅转发文本/媒体的核心字段；卡片消息按其 content 转发文本（不复制 payload）
    // - 失败一条不阻塞其他，返回 { successCount, failures: [{ originalId, error }] }
    async forwardSelected({ targetFlashNoteId, currentUserId } = {}) {
      const ids = Array.from(this.selectedIds).filter((v) => v != null)
      if (ids.length === 0) throw new Error('未选择任何消息')
      if (targetFlashNoteId == null) throw new Error('未选择目标闪记')

      // 保留原顺序：messages 按 createdAt 升序，filter 后顺序与原数组一致
      const toForward = this.messages.filter((m) => m && ids.includes(m.id))
      const failures = []
      let successCount = 0

      for (const msg of toForward) {
        try {
          await sendMessageApi({
            flashNoteId: targetFlashNoteId,
            content: msg.content == null ? '' : String(msg.content),
            // 媒体字段对原所有者仍可访问；后端会按当前用户做权限校验
            mediaType: msg.mediaType || null,
            mediaUrl: msg.mediaUrl || null,
            fileName: msg.fileName || null,
            fileSize: msg.fileSize || null,
            mediaDuration: msg.mediaDuration || null,
            thumbnailUrl: msg.thumbnailUrl || null
          })
          successCount += 1
        } catch (e) {
          failures.push({
            originalId: msg.id,
            error: e?.serverMessage || e?.message || '转发失败'
          })
        }
      }

      this.selectedIds = new Set()
      this.selectMode = false
      void currentUserId
      return { successCount, failures }
    },

    // D1-W16-01 清空收集箱
    // 仅在当前会话是收集箱（flashNoteId === -1）时合理；其他会话不允许调用
    async clearInbox() {
      if (!isInboxFlashNoteId(this.flashNoteId)) {
        throw new Error('当前会话不是收集箱')
      }
      try {
        await clearInboxApi()
        this.messages = []
        this.total = 0
        // hasMore 是 getter（page < pages），通过设置 page=pages 让 hasMore 变为 false
        this.page = 1
        this.pages = 1
        this.selectedIds = new Set()
        this.selectMode = false
      } catch (e) {
        this.error = e?.serverMessage || e?.message || '清空失败'
        throw e
      }
    },

    enterSelectMode() {
      this.selectMode = true
      this.selectedIds = new Set()
    },

    exitSelectMode() {
      this.selectMode = false
      this.selectedIds = new Set()
    },

    toggleSelect(id) {
      if (id == null) return
      const next = new Set(this.selectedIds)
      if (next.has(id)) {
        next.delete(id)
      } else {
        next.add(id)
      }
      this.selectedIds = next
    },

    reset() {
      this.flashNoteId = null
      this.messages = []
      this.loading = false
      this.sending = false
      this.loadingMore = false
      this.error = null
      this.page = 0
      this.pageSize = DEFAULT_PAGE_SIZE
      this.pages = 0
      this.total = 0
      this.selectMode = false
      this.selectedIds = new Set()
    }
  }
})

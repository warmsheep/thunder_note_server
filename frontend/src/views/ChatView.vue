<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useChatStore } from '../stores/chat'
import { useFlashNotesStore } from '../stores/flashNotes'
import { useFavoritesStore } from '../stores/favorites'
import { useAuthStore } from '../stores/auth'
import { useContactsStore } from '../stores/contacts'
import { useToast } from '../composables/useToast'
import { isOwnMessage, isInboxFlashNoteId } from '../utils/messageHelpers'
import LoadingState from '../components/LoadingState.vue'
import ErrorState from '../components/ErrorState.vue'
import EmptyState from '../components/EmptyState.vue'
import MessageBubble from '../components/MessageBubble.vue'
import MessageComposer from '../components/MessageComposer.vue'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import AuthenticatedAvatar from '../components/AuthenticatedAvatar.vue'
import CardEditorDialog from '../components/CardEditorDialog.vue'
import CardDetailDialog from '../components/CardDetailDialog.vue'
import { uploadFile, triggerDownload } from '../api/files'
import { inferMediaType } from '../utils/fileHelpers'
import { useChatScroll } from '../composables/useChatScroll'
import { loadDraft, saveDraft, clearDraft } from '../composables/useChatDraft'

// D1-W6 / D1-W20 单条会话页（独立顶级路由）。
// W20 后同一个 ChatView 同时承担两种会话身份：
//   - 闪记会话（route.name === 'chat'）：route.params.flashNoteId
//   - 联系人 1v1（route.name === 'contact-chat'）：route.params.peerUserId
// store / useChatScroll 都是按 mode 驱动；这里仅负责从路由读出身份、调起 store、
// 在 header 里提供不同的标题 / 图标 / 状态。
//
// - 进入时根据当前身份拉首页（page=1, limit=30）
// - 滚动到顶部触发 loadMore
// - 发送：optimistic + serverMessage 替换；失败保留输入并标红消息
// - 删除：单条 + 多选两种入口，都走 ConfirmDialog 二次确认
// - 联系人模式下不显示“清空收集箱”按钮。

const route = useRoute()
const router = useRouter()
const chatStore = useChatStore()
const flashNotesStore = useFlashNotesStore()
const favoritesStore = useFavoritesStore()
const authStore = useAuthStore()
const contactsStore = useContactsStore()
const { showSuccess, showError } = useToast()

const composerRef = ref(null)
const uploadProgress = ref(0)
const uploading = ref(false)
const draftLoadedKey = ref(null)

// W20 从路由参数推出会话身份。
// route.name === 'contact-chat' 时走 peerUserId 模式；
// 其余（'chat'）走 flashNoteId 模式，含收集箱 -1。
// D1-W28-08 两种会话统一走传统 IM 布局：底部 composer + 顶老下新（
// W28-06 的微博风布局因与发送体验不一致已回滚）。
const isContactRoute = computed(() => route.name === 'contact-chat')
const peerUserId = computed(() =>
  isContactRoute.value && route.params.peerUserId != null ? Number(route.params.peerUserId) : null
)
const flashNoteId = computed(() =>
  isContactRoute.value || route.params.flashNoteId == null
    ? null
    : Number(route.params.flashNoteId)
)

// useChatScroll 驱动用的会话键（与 store getter 一致）
const conversationKey = computed(() => {
  if (peerUserId.value != null) return `peer:${peerUserId.value}`
  if (flashNoteId.value != null) return `fn:${flashNoteId.value}`
  return null
})

// D1-W19 滚动行为统一：scrollerRef / scrollToBottom / handleScroll / 新消息提示
// / sessionStorage 位置记忆等都封装在 useChatScroll 里。
const {
  scrollerRef,
  hasNewBelow,
  scrollToBottom,
  handleScroll,
  rememberScroll,
  restoreScrollOrBottom
} = useChatScroll({
  conversationKey,
  messages: computed(() => chatStore.messages),
  shouldLoadMore: () => chatStore.hasMore && !chatStore.loadingMore,
  onLoadMore: () => chatStore.loadMore()
  // D1-W28-08 不再传 reversed：微博风布局已回滚。useChatScroll 的 reversed 参数其实现与测试保留，
  // 以便后续需要反转语义的场景（如复用到资讯流页面）零成本接入。
})

// W20 联系人信息：从 contactsStore 查找当前 peer；不一定在列表内（刚加的朋友或路由直进场景）
const peerContact = computed(() => {
  if (peerUserId.value == null) return null
  return contactsStore.findContactById(peerUserId.value)
})

const headerTitle = computed(() => {
  if (isContactRoute.value) {
    const c = peerContact.value
    if (c) return c.nickname || c.username || `用户 ${peerUserId.value}`
    return `用户 ${peerUserId.value ?? ''}`
  }
  if (isInboxFlashNoteId(flashNoteId.value)) {
    return '收集箱'
  }
  // 优先从 flashNotes store 读 title；store 没数据时回退一个占位标题
  const note = flashNotesStore.list.find((n) => n && Number(n.id) === flashNoteId.value)
  return note?.title || '闪记会话'
})

const headerIcon = computed(() => {
  if (isContactRoute.value) {
    const c = peerContact.value
    if (c && c.avatar) return null // 使用 AuthenticatedAvatar 渲染
    return '👤'
  }
  if (isInboxFlashNoteId(flashNoteId.value)) return '📥'
  const note = flashNotesStore.list.find((n) => n && Number(n.id) === flashNoteId.value)
  return note?.icon || '⚡'
})

// W20 背景：联系人模式备用头像 URL（造一个给 AuthenticatedAvatar 的轻量赋值）
const peerAvatar = computed(() => peerContact.value?.avatar || null)

const currentUserId = computed(() => authStore.user?.id ?? null)
const draftScope = computed(() => `user:${currentUserId.value ?? 'anon'}`)

const initialLoading = computed(() => chatStore.loading && chatStore.messages.length === 0)
const showError_ = computed(() => Boolean(chatStore.error) && chatStore.messages.length === 0)
const showEmpty = computed(
  () => !chatStore.loading && !chatStore.error && chatStore.messages.length === 0
)

// 删除确认
const deleteDialog = ref({ open: false, mode: 'single', target: null, busy: false })

// D1-W16-01 收集箱清空确认
const clearInboxDialog = ref({ open: false, busy: false })

// D1-W17-01 合并为卡片对话框
const mergeDialog = ref({ open: false, busy: false, title: '' })

// D1-W17-02 卡片详情对话框（只读，后端无 update 接口）
const cardDetailDialog = ref({ open: false, message: null })

// D1-W17-03 / D1-W20-05 转发对话框（支持会话或联系人作为目标）
// targetType: 'flash' | 'peer'。UI 上用 tab 切换，避免一个列表则含二二混淆。
//
// D1-W21-03 字段增强：同一对话框同时服务「多选转发」与「单条转发」：
//   - mode: 'multi' 走 chatStore.forwardSelected（会清 selectedIds + selectMode）
//   - mode: 'single' 走 chatStore.forwardMessages({ ids: [singleId] })，不动其他状态
const forwardDialog = ref({
  open: false,
  busy: false,
  mode: 'multi', // 'multi' | 'single'
  singleMessageId: null,
  targetType: 'flash',
  targetFlashNoteId: null,
  targetPeerUserId: null
})

function resetForwardDialog() {
  forwardDialog.value = {
    open: false,
    busy: false,
    mode: 'multi',
    singleMessageId: null,
    targetType: 'flash',
    targetFlashNoteId: null,
    targetPeerUserId: null
  }
}

const forwardableNotes = computed(() => {
  // 排除当前会话（仅 flash 模式下才可能匹配）；优先列出非 hidden + 非 deleted 的闪记
  return (flashNotesStore.list || []).filter(
    (n) => n
      && !n.deleted
      && !n.hidden
      && (isContactRoute.value || Number(n.id) !== flashNoteId.value)
  )
})

// W20-05 可选联系人：仅正式好友 (FRIEND)；联系人会话本身转发时排除自己
const forwardableContacts = computed(() => {
  return (contactsStore.contacts || []).filter(
    (c) => c
      && c.relationStatus === 'FRIEND'
      && (peerUserId.value == null || Number(c.userId) !== peerUserId.value)
  )
})

const isInbox = computed(() => !isContactRoute.value && isInboxFlashNoteId(flashNoteId.value))

function askClearInbox() {
  clearInboxDialog.value = { open: true, busy: false }
}

function cancelClearInbox() {
  if (clearInboxDialog.value.busy) return
  clearInboxDialog.value = { open: false, busy: false }
}

async function confirmClearInbox() {
  clearInboxDialog.value.busy = true
  try {
    await chatStore.clearInbox()
    clearInboxDialog.value = { open: false, busy: false }
    showSuccess('收集箱已清空')
    // 同步刷新闪记列表，让收集箱项的 latestMessage 也更新
    flashNotesStore.fetchList({ silent: true }).catch(() => {})
  } catch (e) {
    clearInboxDialog.value.busy = false
    showError(e?.serverMessage || e?.message || '清空失败')
  }
}

async function reload() {
  if (isContactRoute.value) {
    if (peerUserId.value == null || Number.isNaN(peerUserId.value)) {
      router.replace('/contacts')
      return
    }
    try {
      await chatStore.openConversation({ peerUserId: peerUserId.value })
      await restoreScrollOrBottom()
    } catch (_e) {
      // store.error 驱动
    }
    return
  }
  if (flashNoteId.value == null || Number.isNaN(flashNoteId.value)) {
    router.replace('/notes')
    return
  }
  try {
    await chatStore.openConversation({ flashNoteId: flashNoteId.value })
    // W19-02 进入会话优先恢复 sessionStorage 上次位置，没有再滚到底
    await restoreScrollOrBottom()
  } catch (_e) {
    // 错误展示由 store.error 驱动
  }
}

function restoreDraftIfNeeded() {
  const key = conversationKey.value
  if (!key || !composerRef.value || draftLoadedKey.value === key) return
  composerRef.value.setText(loadDraft(key, draftScope.value))
  draftLoadedKey.value = key
}

// D1-W24-05 持久化当前 composer 里的未发送文本到指定 conversationKey
// keyOverride：watch 切换会话时，`conversationKey` computed 已经指向新路由，
// 这里必须显式传入「切换前」的 previousKey，否则会把旧草稿写到新会话的槽位，
// 并在下一帧被 restoreDraftIfNeeded 反向污染成当前输入。
function persistCurrentDraft(keyOverride) {
  const key = keyOverride ?? conversationKey.value
  if (!key) return
  saveDraft(key, composerRef.value?.getText?.() || '', draftScope.value)
}

// 根据 route.name + 路由参数反推 conversationKey；watch 的 prev 快照里要用到
function deriveConversationKey(name, flashNoteIdParam, peerUserIdParam) {
  if (name === 'contact-chat') {
    const pid = Number(peerUserIdParam)
    return Number.isFinite(pid) ? `peer:${pid}` : null
  }
  if (name === 'chat') {
    const fid = Number(flashNoteIdParam)
    return Number.isFinite(fid) ? `fn:${fid}` : null
  }
  return null
}

onMounted(() => {
  // 列表 store 没加载时顺手拉一下，便于头部显示真实 title/icon
  if (!flashNotesStore.loaded) {
    flashNotesStore.fetchList({ silent: true }).catch(() => {})
  }
  // 收藏集 silent 拉一次，便于消息气泡显示当前收藏状态
  if (!favoritesStore.loaded) {
    favoritesStore.fetchList({ silent: true }).catch(() => {})
  }
  // W20 联系人模式需要联系人列表才能显示对方昵称/头像；转发对话框也需要
  if (!contactsStore.contactsLoaded) {
    contactsStore.fetchContacts({ silent: true }).catch(() => {})
  }
  reload()
  nextTick(() => {
    restoreDraftIfNeeded()
  })
})

onBeforeUnmount(() => {
  // W19-02 离开会话前记下当前 scrollTop（按 flashNoteId 隔离）
  rememberScroll()
  persistCurrentDraft()
  chatStore.reset()
})

// W20 路由参数变化： flashNoteId / peerUserId / route.name 任一变 → 切会话。
// 切之前先 rememberScroll，再 reload。
watch(
  () => [route.name, route.params.flashNoteId, route.params.peerUserId],
  ([nextName, nextFn, nextPeer], prev) => {
    if (nextName !== 'chat' && nextName !== 'contact-chat') return
    const [prevName, prevFn, prevPeer] = prev || []
    const changed =
      prevName !== nextName
      || String(prevFn) !== String(nextFn)
      || String(prevPeer) !== String(nextPeer)
    if (prevName != null && changed) {
      rememberScroll()
      // 用 prev 快照算出切换前的 conversationKey；如果直接读
      // conversationKey.value 会拿到新路由的 key（见 persistCurrentDraft 注释）
      const previousKey = deriveConversationKey(prevName, prevFn, prevPeer)
      if (previousKey) {
        persistCurrentDraft(previousKey)
      }
    }
    draftLoadedKey.value = null
    reload()
    nextTick(() => {
      restoreDraftIfNeeded()
    })
  }
)

// MessageComposer @submit 的 payload 是 { text, files, file }
//   - text：字符串文本（可能为空）
//   - files：File[]（W22-04 多附件，0~9 个）
//   - file：files[0]（向后兼容老调用）
// 流程：
//   - 0 附件：直接发文本
//   - 1+ 附件：依次串行 upload + send；
//     第一条带 text 作为 caption（与 Android 行为一致），后续 N-1 条只带 media
async function handleSend(payload) {
  const text = (payload && typeof payload.text === 'string') ? payload.text : ''
  const files = Array.isArray(payload?.files)
    ? payload.files.filter(Boolean)
    : (payload?.file ? [payload.file] : [])

  // 无附件：单文本
  if (files.length === 0) {
    if (!text.trim()) return
    try {
      await chatStore.send({
        content: text,
        currentUserId: currentUserId.value,
        media: null
      })
      clearDraft(conversationKey.value, draftScope.value)
      composerRef.value?.reset()
      await nextTick()
      scrollToBottom({ smooth: true })
    } catch (e) {
      showError(e?.serverMessage || e?.message || '发送失败')
    }
    return
  }

  // 有附件：依次串行处理；任一失败即终止剩余 files，已发出的不回滚
  uploading.value = true
  uploadProgress.value = 0
  let sentCount = 0
  try {
    for (let i = 0; i < files.length; i++) {
      const file = files[i]
      const result = await uploadFile(file, {
        onUploadProgress: (e) => {
          if (e && e.total) {
            uploadProgress.value = e.loaded / e.total
          }
        }
      })
      const objectName = result?.objectName
      if (!objectName) {
        throw new Error('上传失败：缺少 objectName')
      }
      const media = {
        mediaType: inferMediaType(file),
        mediaUrl: objectName,
        fileName: result?.originalFilename || file.name,
        fileSize: file.size != null ? Number(file.size) : null
      }
      // W22-04 第一条携带 text 作为 caption；后续仅 media
      const contentForThis = i === 0 ? text : ''
      await chatStore.send({
        content: contentForThis,
        currentUserId: currentUserId.value,
        media
      })
      sentCount += 1
      uploadProgress.value = 0
    }
    clearDraft(conversationKey.value, draftScope.value)
    composerRef.value?.reset()
    await nextTick()
    scrollToBottom({ smooth: true })
  } catch (e) {
    if (sentCount > 0) {
      showError(`已发送 ${sentCount}/${files.length} 个附件；剩余失败：${e?.serverMessage || e?.message || '未知错误'}`)
    } else {
      showError(e?.serverMessage || e?.message || '附件上传失败')
    }
  } finally {
    uploading.value = false
    uploadProgress.value = 0
  }
}

// D1-W27-02 录音消息：MessageComposer 通过 submit-voice emit 把录音 blob 投出来，
// 这里复用 uploadFile + chatStore.send，专门走 mediaType='voice' + mediaDuration（秒）。
// 与 W22 文件上传链路同源，唯一不同点是 mediaType 写死为 'voice'，避免 inferMediaType 把
// audio/webm 推断成 'audio'（导致后端 displayText 仍按 [图片]/[语音] 分类）。
async function handleSendVoice(payload) {
  const blob = payload && payload.blob
  if (!blob || !blob.size) {
    showError('录音内容为空')
    return
  }
  const fileName = payload.fileName || `voice-${Date.now()}.webm`
  const durationSec = Math.max(1, Number(payload.durationSec) || 1)
  const mimeType = payload.mimeType || blob.type || 'audio/webm'
  // 把 Blob 包成 File（带原始 mimeType）以便 uploadFile 能正确填 multipart Content-Type
  let file
  try {
    file = new File([blob], fileName, { type: mimeType })
  } catch (_e) {
    // 老浏览器 fallback：手动给 blob 加 name 属性，uploadFile 内部用 FormData append 会兜底
    file = blob
    file.name = fileName
  }

  uploading.value = true
  uploadProgress.value = 0
  try {
    const result = await uploadFile(file, {
      onUploadProgress: (e) => {
        if (e && e.total) {
          uploadProgress.value = e.loaded / e.total
        }
      }
    })
    const objectName = result?.objectName
    if (!objectName) {
      throw new Error('上传失败：缺少 objectName')
    }
    await chatStore.send({
      content: '',
      currentUserId: currentUserId.value,
      media: {
        mediaType: 'voice',
        mediaUrl: objectName,
        fileName: result?.originalFilename || fileName,
        fileSize: blob.size != null ? Number(blob.size) : null,
        mediaDuration: durationSec
      }
    })
    await nextTick()
    scrollToBottom({ smooth: true })
  } catch (e) {
    showError(e?.serverMessage || e?.message || '语音发送失败')
  } finally {
    uploading.value = false
    uploadProgress.value = 0
  }
}

// W22-04 附件溢出提示（超过 9 个）
function onComposerOverflow(payload) {
  const max = payload?.max || 9
  showError(`一次最多 ${max} 个附件`)
}

async function handleRetry(clientRequestId) {
  try {
    await chatStore.retryFailed(clientRequestId, currentUserId.value)
  } catch (e) {
    showError(e?.serverMessage || e?.message || '重试失败')
  }
}

async function handleToggleFavorite(message) {
  if (!message || message.id == null) return
  const isFav = favoritesStore.isFavorited(message.id)
  try {
    if (isFav) {
      await favoritesStore.remove(message.id)
      showSuccess('已取消收藏')
    } else {
      await favoritesStore.add(message.id)
      showSuccess('已收藏')
    }
  } catch (e) {
    showError(e?.serverMessage || e?.message || '操作失败')
  }
}

async function handleDownloadMessage(message) {
  if (!message?.mediaUrl) return
  try {
    await triggerDownload(message.mediaUrl, message.fileName || 'download')
    showSuccess('已开始下载')
  } catch (e) {
    showError(e?.serverMessage || e?.message || '下载失败')
  }
}

function askDeleteSingle(message) {
  deleteDialog.value = { open: true, mode: 'single', target: message, busy: false }
}

function askDeleteBatch() {
  if (chatStore.selectedIds.size === 0) {
    showError('请选择消息')
    return
  }
  deleteDialog.value = { open: true, mode: 'batch', target: null, busy: false }
}

function cancelDelete() {
  deleteDialog.value = { open: false, mode: 'single', target: null, busy: false }
}

async function confirmDelete() {
  deleteDialog.value.busy = true
  try {
    if (deleteDialog.value.mode === 'batch') {
      await chatStore.batchRemove()
      showSuccess('已删除')
    } else if (deleteDialog.value.target) {
      await chatStore.remove(deleteDialog.value.target.id)
      showSuccess('已删除')
    }
    deleteDialog.value = { open: false, mode: 'single', target: null, busy: false }
  } catch (e) {
    deleteDialog.value.busy = false
    showError(e?.serverMessage || e?.message || '删除失败')
  }
}

function goBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    // W20: 联系人会话 fallback 回联系人页；闪记会话回主列表
    router.replace(isContactRoute.value ? '/contacts' : '/notes')
  }
}

function toggleSelectMode() {
  if (chatStore.selectMode) {
    chatStore.exitSelectMode()
  } else {
    chatStore.enterSelectMode()
  }
}

// D1-W17-01 合并为卡片
function askMerge() {
  if (chatStore.selectedIds.size === 0) {
    showError('请先选择消息')
    return
  }
  if (chatStore.selectedIds.size > 50) {
    showError('单次最多合并 50 条')
    return
  }
  mergeDialog.value = { open: true, busy: false, title: '' }
}
function cancelMerge() {
  if (mergeDialog.value.busy) return
  mergeDialog.value = { open: false, busy: false, title: '' }
}
async function confirmMerge() {
  const title = mergeDialog.value.title.trim()
  if (!title) {
    showError('请输入卡片标题')
    return
  }
  mergeDialog.value.busy = true
  try {
    await chatStore.mergeSelected({ title })
    mergeDialog.value = { open: false, busy: false, title: '' }
    showSuccess('已合并为卡片')
    await nextTick()
    // W19-03 合并卡片成功后跟随到底
    scrollToBottom({ smooth: true })
  } catch (e) {
    mergeDialog.value.busy = false
    showError(e?.serverMessage || e?.message || '合并失败')
  }
}

// D1-W17-02 卡片详情
function openCardDetail(message) {
  if (!message) return
  cardDetailDialog.value = { open: true, message }
}
function closeCardDetail() {
  cardDetailDialog.value = { open: false, message: null }
}

// D1-W17-03 / D1-W20-05 / D1-W21-03 转发
function prepareForwardLists() {
  if (!flashNotesStore.loaded) {
    flashNotesStore.fetchList({ silent: true }).catch(() => {})
  }
  if (!contactsStore.contactsLoaded) {
    contactsStore.fetchContacts({ silent: true }).catch(() => {})
  }
}

// 多选转发：header 「转发」按钮
function askForward() {
  if (chatStore.selectedIds.size === 0) {
    showError('请先选择消息')
    return
  }
  prepareForwardLists()
  forwardDialog.value = {
    open: true,
    busy: false,
    mode: 'multi',
    singleMessageId: null,
    targetType: 'flash',
    targetFlashNoteId: null,
    targetPeerUserId: null
  }
}

// W21-03 单条转发：MessageBubble 上下文菜单 「转发」按钮
function askForwardSingle(message) {
  if (!message || message.id == null) return
  prepareForwardLists()
  forwardDialog.value = {
    open: true,
    busy: false,
    mode: 'single',
    singleMessageId: message.id,
    targetType: 'flash',
    targetFlashNoteId: null,
    targetPeerUserId: null
  }
}

function cancelForward() {
  if (forwardDialog.value.busy) return
  resetForwardDialog()
}

function setForwardTab(type) {
  if (forwardDialog.value.busy) return
  forwardDialog.value.targetType = type
  forwardDialog.value.targetFlashNoteId = null
  forwardDialog.value.targetPeerUserId = null
}

async function confirmForward() {
  const fwd = forwardDialog.value
  const targetFlashNoteId = fwd.targetType === 'flash' ? fwd.targetFlashNoteId : null
  const targetPeerUserId = fwd.targetType === 'peer' ? fwd.targetPeerUserId : null
  if (targetFlashNoteId == null && targetPeerUserId == null) {
    showError(fwd.targetType === 'peer' ? '请选择目标联系人' : '请选择目标闪记')
    return
  }
  forwardDialog.value.busy = true
  try {
    let result
    if (fwd.mode === 'single' && fwd.singleMessageId != null) {
      result = await chatStore.forwardMessages({
        ids: [fwd.singleMessageId],
        targetFlashNoteId,
        targetPeerUserId
      })
    } else {
      result = await chatStore.forwardSelected({
        targetFlashNoteId,
        targetPeerUserId,
        currentUserId: currentUserId.value
      })
    }
    const { successCount, failures } = result
    resetForwardDialog()
    if (failures.length === 0) {
      showSuccess(`已转发 ${successCount} 条`)
    } else if (successCount === 0) {
      showError(`转发失败：${failures[0].error}`)
    } else {
      showError(`部分转发失败（成功 ${successCount} / 失败 ${failures.length}）`)
    }
  } catch (e) {
    forwardDialog.value.busy = false
    showError(e?.serverMessage || e?.message || '转发失败')
  }
}

// W21-01 上下文菜单 「多选」：进多选模式并预选中该条
function enterSelectModeWith(message) {
  if (!message || message.id == null) return
  if (!chatStore.selectMode) chatStore.enterSelectMode()
  chatStore.toggleSelect(message.id)
}

// W22-03 多媒体卡片新建对话框
const cardEditorDialog = ref({ open: false })
function openCardEditor() {
  cardEditorDialog.value = { open: true }
}
function onCardCreated() {
  showSuccess('卡片已创建')
  // 让 chat 视图把刚创建的 COMPOSITE 卡片消息拉进来
  reload()
}

// W21-02 MessageBubble forward-single 事件在 type='copied' 时上报，这里仅负责 toast
function onBubbleForwardSingle(payload) {
  if (!payload || typeof payload !== 'object') return
  if (payload.type === 'copied') {
    showSuccess('已复制')
    return
  }
  if (payload.type === 'forward') {
    askForwardSingle(payload.message)
  }
}
</script>

<template>
  <div class="chat-page">
    <header class="chat-header">
      <button type="button" class="header-back" @click="goBack" aria-label="返回">←</button>
      <div class="header-title">
        <!-- W20: 联系人模式且有头像时使用 AuthenticatedAvatar，其余走 emoji icon -->
        <AuthenticatedAvatar
          v-if="isContactRoute && peerAvatar"
          class="header-avatar"
          :avatar="peerAvatar"
          :fallback="(headerTitle || '?').slice(0, 1).toUpperCase()"
          :size="28"
        />
        <span v-else class="header-icon" aria-hidden="true">{{ headerIcon }}</span>
        <span class="header-text">{{ headerTitle }}</span>
      </div>
      <div class="header-actions">
        <button type="button" class="header-btn" @click="toggleSelectMode">
          {{ chatStore.selectMode ? '取消多选' : '多选' }}
        </button>
        <template v-if="chatStore.selectMode">
          <button
            type="button"
            class="header-btn"
            :disabled="chatStore.selectedIds.size === 0"
            @click="askMerge"
            title="合并所选消息为卡片"
          >合并</button>
          <button
            type="button"
            class="header-btn"
            :disabled="chatStore.selectedIds.size === 0"
            @click="askForward"
            title="转发所选消息到其他闪记"
          >转发</button>
          <button
            type="button"
            class="header-btn danger"
            :disabled="chatStore.selectedIds.size === 0"
            @click="askDeleteBatch"
          >删除（{{ chatStore.selectedIds.size }}）</button>
        </template>
        <button
          v-if="isInbox && !chatStore.selectMode"
          type="button"
          class="header-btn danger"
          :disabled="chatStore.messages.length === 0"
          @click="askClearInbox"
          title="清空收集箱内全部消息"
        >清空</button>
      </div>
    </header>

    <main
      class="chat-scroll"
      ref="scrollerRef"
      @scroll="handleScroll"
    >
      <LoadingState v-if="initialLoading" text="加载消息中..." />
      <ErrorState
        v-else-if="showError_"
        :message="chatStore.error"
        @retry="reload"
      />
      <template v-else>
        <div v-if="chatStore.loadingMore" class="load-more-tip">加载历史中...</div>
        <EmptyState
          v-if="showEmpty"
          icon="💬"
          title="还没有消息"
          description="在下方输入并按 Enter 发送第一条消息"
        />
        <MessageBubble
          v-for="m in chatStore.messages"
          :key="m.id != null ? `id:${m.id}` : `cr:${m.clientRequestId}`"
          :message="m"
          :mine="!isContactRoute || isOwnMessage(m, currentUserId)"
          :select-mode="chatStore.selectMode"
          :selected="m.id != null && chatStore.selectedIds.has(m.id)"
          :favorited="m.id != null && favoritesStore.isFavorited(m.id)"
          @toggle-select="(id) => chatStore.toggleSelect(id)"
          @delete="askDeleteSingle"
          @retry="handleRetry"
          @toggle-favorite="handleToggleFavorite"
          @download="handleDownloadMessage"
          @open-card="openCardDetail"
          @forward-single="onBubbleForwardSingle"
          @enter-select-with="enterSelectModeWith"
        />
      </template>

      <!-- D1-W19-01 回到底部悬浮按钮：仅当用户滑出底部时显示；hasNewBelow 表示有新消息到达 -->
      <button
        v-if="hasNewBelow"
        type="button"
        class="jump-to-bottom"
        :title="'有新消息，点击回到底部'"
        @click="scrollToBottom({ smooth: true })"
      >
        <span class="jump-arrow" aria-hidden="true">⬇</span>
        <span class="jump-text">新消息</span>
      </button>
    </main>

    <MessageComposer
      ref="composerRef"
      :busy="chatStore.sending || uploading"
      :upload-progress="uploadProgress"
      @submit="handleSend"
      @submit-voice="handleSendVoice"
      @open-card-editor="openCardEditor"
      @overflow="onComposerOverflow"
    />

    <!-- W22-03 多媒体卡片新建对话框 -->
    <CardEditorDialog
      v-model:open="cardEditorDialog.open"
      :flash-note-id="isContactRoute ? null : flashNoteId"
      :peer-user-id="isContactRoute ? peerUserId : null"
      @created="onCardCreated"
    />

    <ConfirmDialog
      :open="deleteDialog.open"
      :busy="deleteDialog.busy"
      title="删除消息"
      :message="deleteDialog.mode === 'batch'
        ? `确认删除选中的 ${chatStore.selectedIds.size} 条消息吗？此操作不可撤销。`
        : '确认删除这条消息吗？此操作不可撤销。'"
      confirm-label="删除"
      danger
      @confirm="confirmDelete"
      @cancel="cancelDelete"
    />

    <ConfirmDialog
      :open="clearInboxDialog.open"
      :busy="clearInboxDialog.busy"
      title="清空收集箱"
      message="确认清空收集箱内的全部消息？此操作不可撤销。"
      confirm-label="清空"
      danger
      @confirm="confirmClearInbox"
      @cancel="cancelClearInbox"
    />

    <!-- D1-W17-01 合并为卡片：标题输入弹窗 -->
    <div v-if="mergeDialog.open" class="modal-overlay" @click.self="cancelMerge">
      <div class="modal" role="dialog" aria-label="合并为卡片">
        <header class="modal-header">
          <h2 class="modal-title">合并为卡片</h2>
          <button type="button" class="modal-close" @click="cancelMerge">×</button>
        </header>
        <div class="modal-body">
          <p class="modal-desc">
            将所选 {{ chatStore.selectedIds.size }} 条消息合并为一张卡片消息（原消息不会被删除）。
          </p>
          <input
            v-model="mergeDialog.title"
            type="text"
            class="modal-input"
            placeholder="请输入卡片标题"
            maxlength="64"
            :disabled="mergeDialog.busy"
            @keydown.enter.prevent="confirmMerge"
          />
        </div>
        <footer class="modal-footer">
          <button type="button" class="btn-secondary" :disabled="mergeDialog.busy" @click="cancelMerge">取消</button>
          <button
            type="button"
            class="btn-primary"
            :disabled="mergeDialog.busy || !mergeDialog.title.trim()"
            @click="confirmMerge"
          >{{ mergeDialog.busy ? '合并中...' : '合并' }}</button>
        </footer>
      </div>
    </div>

    <!-- D1-W17-02 / D1-W25-02 卡片详情：复用 CardDetailDialog 组件，
         每个 item 真实渲染图片 / 视频 / 音频 / 文件 / 文本，与 Android `CardDetailActivity` 对齐 -->
    <CardDetailDialog
      v-model:open="cardDetailDialog.open"
      :message="cardDetailDialog.message"
      @close="closeCardDetail"
    />

    <!-- D1-W17-03 / D1-W20-05 转发：选择目标闪记或联系人 -->
    <div v-if="forwardDialog.open" class="modal-overlay" @click.self="cancelForward">
      <div class="modal" role="dialog" aria-label="转发到">
        <header class="modal-header">
          <h2 class="modal-title">转发到</h2>
          <button type="button" class="modal-close" @click="cancelForward">×</button>
        </header>
        <div class="modal-body">
          <p class="modal-desc">
            <template v-if="forwardDialog.mode === 'single'">将这条消息转发到闪记或联系人。</template>
            <template v-else>将所选 {{ chatStore.selectedIds.size }} 条消息转发到闪记或联系人。</template>
          </p>
          <!-- W20-05 目标类型 tab：闪记 / 联系人，二选一 -->
          <div class="forward-tabs" role="tablist">
            <button
              type="button"
              role="tab"
              :aria-selected="forwardDialog.targetType === 'flash' ? 'true' : 'false'"
              class="forward-tab"
              :class="{ active: forwardDialog.targetType === 'flash' }"
              :disabled="forwardDialog.busy"
              @click="setForwardTab('flash')"
            >闪记会话</button>
            <button
              type="button"
              role="tab"
              :aria-selected="forwardDialog.targetType === 'peer' ? 'true' : 'false'"
              class="forward-tab"
              :class="{ active: forwardDialog.targetType === 'peer' }"
              :disabled="forwardDialog.busy"
              @click="setForwardTab('peer')"
            >联系人</button>
          </div>

          <ul v-if="forwardDialog.targetType === 'flash'" class="forward-list">
            <li
              v-for="n in forwardableNotes"
              :key="`fn-${n.id}`"
              class="forward-item"
              :class="{ active: forwardDialog.targetFlashNoteId === n.id }"
              @click="forwardDialog.targetFlashNoteId = n.id"
            >
              <span class="forward-icon">{{ n.icon || '⚡' }}</span>
              <span class="forward-title">{{ n.title || '未命名闪记' }}</span>
            </li>
            <li v-if="forwardableNotes.length === 0" class="empty-text">没有可转发的目标闪记</li>
          </ul>
          <ul v-else class="forward-list">
            <li
              v-for="c in forwardableContacts"
              :key="`peer-${c.userId}`"
              class="forward-item"
              :class="{ active: forwardDialog.targetPeerUserId === c.userId }"
              @click="forwardDialog.targetPeerUserId = c.userId"
            >
              <AuthenticatedAvatar
                :avatar="c.avatar"
                :fallback="((c.nickname || c.username || '?').slice(0, 1)).toUpperCase()"
                :size="28"
              />
              <span class="forward-title">{{ c.nickname || c.username || `用户 ${c.userId}` }}</span>
            </li>
            <li v-if="forwardableContacts.length === 0" class="empty-text">没有可转发的联系人</li>
          </ul>
        </div>
        <footer class="modal-footer">
          <button type="button" class="btn-secondary" :disabled="forwardDialog.busy" @click="cancelForward">取消</button>
          <button
            type="button"
            class="btn-primary"
            :disabled="forwardDialog.busy || (forwardDialog.targetType === 'flash' ? forwardDialog.targetFlashNoteId == null : forwardDialog.targetPeerUserId == null)"
            @click="confirmForward"
          >{{ forwardDialog.busy ? '转发中...' : '转发' }}</button>
        </footer>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--color-bg);
}

/* D1-W17 modal 样式（合并/卡片详情/转发共用） */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 50;
  padding: 16px;
}
.modal {
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  width: min(420px, 100%);
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  box-shadow: var(--shadow-md);
  overflow: hidden;
}
.modal-large { width: min(520px, 100%); }
.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--color-divider);
}
.modal-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}
.modal-close {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  border: none;
  background: var(--color-divider);
  color: var(--color-text-secondary);
  cursor: pointer;
  font-size: 16px;
  line-height: 1;
}
.modal-body {
  padding: 16px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 10px 16px;
  border-top: 1px solid var(--color-divider);
}
.modal-desc {
  margin: 0;
  font-size: 13px;
  color: var(--color-text-secondary);
}
.modal-input {
  padding: 8px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  font-size: 14px;
  background: var(--color-surface);
  color: var(--color-text-primary);
  outline: none;
}
.modal-input:focus { border-color: var(--color-primary); }

.btn-primary {
  padding: 6px 14px;
  border: none;
  border-radius: var(--radius-md);
  background: var(--color-primary);
  color: #ffffff;
  font-size: 13px;
  cursor: pointer;
}
.btn-primary:disabled { opacity: 0.55; cursor: not-allowed; }
.btn-secondary {
  padding: 6px 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  color: var(--color-text-secondary);
  font-size: 13px;
  cursor: pointer;
}
.btn-secondary:disabled { opacity: 0.55; cursor: not-allowed; }

/* W20-05 转发对话框 tab：闪记 / 联系人 */
.forward-tabs {
  display: flex;
  gap: 4px;
  padding: 4px;
  background: var(--color-bg);
  border-radius: var(--radius-md);
  margin-bottom: 12px;
}
.forward-tab {
  flex: 1;
  padding: 6px 12px;
  border: none;
  background: transparent;
  color: var(--color-text-secondary);
  font-size: 13px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
.forward-tab:hover:not(:disabled):not(.active) {
  color: var(--color-text-primary);
}
.forward-tab.active {
  background: var(--color-surface);
  color: var(--color-primary);
  font-weight: 600;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.08);
}
.forward-tab:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.forward-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 280px;
  overflow-y: auto;
}
.forward-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border: 1px solid var(--color-divider);
  border-radius: var(--radius-md);
  cursor: pointer;
}
.forward-item:hover { border-color: var(--color-primary); }
.forward-item.active {
  border-color: var(--color-primary);
  background: var(--color-primary-light);
}
.forward-icon {
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--color-bg);
  border-radius: 50%;
  font-size: 14px;
}
.forward-title {
  font-size: 14px;
  color: var(--color-text-primary);
  word-break: break-word;
}
.empty-text {
  margin: 16px 0;
  text-align: center;
  color: var(--color-text-hint);
  font-size: 13px;
}

.chat-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: var(--color-topbar-bg);
  border-bottom: 1px solid var(--color-divider);
  flex-shrink: 0;
}
.header-back {
  width: 32px;
  height: 32px;
  border: 1px solid var(--color-border);
  border-radius: 50%;
  background: var(--color-surface);
  cursor: pointer;
  font-size: 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.header-back:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}
.header-title {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.header-icon {
  font-size: 18px;
}
/* W20 联系人模式 header 头像：与 header-icon 占位对齐 */
.header-avatar {
  flex: none;
}
.header-text {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.header-actions {
  display: flex;
  gap: 6px;
}
.header-btn {
  padding: 6px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  font-size: 13px;
  color: var(--color-text-secondary);
  cursor: pointer;
}
.header-btn:hover:not(:disabled) {
  border-color: var(--color-primary);
  color: var(--color-primary);
}
.header-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.header-btn.danger:not(:disabled) {
  border-color: var(--color-danger);
  color: var(--color-danger);
}

.chat-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  position: relative; /* 给 jump-to-bottom 提供定位上下文 */
}

/* D1-W28-08 微博风布局已回滚：取消 column-reverse / 顶部 composer 相关样式 */

/* D1-W19-01 回到底部悬浮按钮：sticky + bottom，跟随容器滚动而非整页固定 */
.jump-to-bottom {
  position: sticky;
  bottom: 8px;
  align-self: center;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border-radius: 999px;
  border: none;
  background: var(--color-primary);
  color: #ffffff;
  font-size: 13px;
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.18);
  /* 让按钮浮在最新消息上方一点点；负 margin 避免占用 chat 列表本身的空间 */
  margin-top: -36px;
  margin-bottom: 4px;
  z-index: 5;
  transition: transform 0.15s, background 0.15s;
}
.jump-to-bottom:hover {
  background: var(--color-primary-dark);
  transform: translateY(-1px);
}
.jump-arrow {
  font-size: 14px;
  line-height: 1;
}
.jump-text {
  font-size: 12px;
}
.load-more-tip {
  text-align: center;
  font-size: 12px;
  color: var(--color-text-hint);
  padding: 8px 0;
}

/* W12-01 桌面超宽屏聊天体验：消息区两侧 padding 自动扩大，
   把消息列宽视觉收窄到 var(--chat-max-width) 居中，
   避免在 4K 屏上一行气泡跨度过大；header / composer 保持全宽以贴合页面框架。
   max() 兜底，确保窄屏时不会出现负 padding。 */
@media (min-width: 1280px) {
  .chat-scroll {
    padding-left: max(16px, calc((100% - var(--chat-max-width)) / 2));
    padding-right: max(16px, calc((100% - var(--chat-max-width)) / 2));
  }
}

/* W12-02 移动端 ≤480px 收紧聊天 header 与 padding */
@media (max-width: 480px) {
  .chat-scroll {
    padding: 12px;
  }
}
</style>

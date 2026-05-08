// D1-W9 文件辅助纯函数

const IMAGE_EXTENSIONS = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp', 'svg', 'avif', 'heic']
const VIDEO_EXTENSIONS = ['mp4', 'mov', 'webm', 'avi', 'mkv', 'm4v', '3gp']
const AUDIO_EXTENSIONS = ['mp3', 'wav', 'ogg', 'flac', 'aac', 'm4a', 'amr']

const IMAGE_PREFIXES = ['image/']
const VIDEO_PREFIXES = ['video/']
const AUDIO_PREFIXES = ['audio/']

const KB = 1024
const MB = 1024 * 1024
const GB = 1024 * 1024 * 1024

export function formatFileSize(bytes) {
  if (bytes == null || Number.isNaN(Number(bytes))) return ''
  const n = Number(bytes)
  if (n < KB) return `${n} B`
  if (n < MB) return `${(n / KB).toFixed(1)} KB`
  if (n < GB) return `${(n / MB).toFixed(1)} MB`
  return `${(n / GB).toFixed(2)} GB`
}

export function getExtension(name) {
  if (!name) return ''
  const dot = String(name).lastIndexOf('.')
  if (dot < 0) return ''
  return String(name).slice(dot + 1).toLowerCase()
}

function inExt(name, list) {
  return list.includes(getExtension(name))
}

function inMime(mime, prefixes) {
  if (!mime) return false
  return prefixes.some((p) => String(mime).toLowerCase().startsWith(p))
}

export function isImage({ mediaType, fileName, contentType } = {}) {
  if (mediaType === 'image') return true
  if (inMime(contentType, IMAGE_PREFIXES)) return true
  if (inExt(fileName, IMAGE_EXTENSIONS)) return true
  return false
}

export function isVideo({ mediaType, fileName, contentType } = {}) {
  if (mediaType === 'video') return true
  if (inMime(contentType, VIDEO_PREFIXES)) return true
  if (inExt(fileName, VIDEO_EXTENSIONS)) return true
  return false
}

export function isAudio({ mediaType, fileName, contentType } = {}) {
  if (mediaType === 'audio') return true
  if (inMime(contentType, AUDIO_PREFIXES)) return true
  if (inExt(fileName, AUDIO_EXTENSIONS)) return true
  return false
}

// 根据浏览器 File 对象推断 mediaType（用于发送时填到 message.mediaType）
export function inferMediaType(file) {
  if (!file) return 'file'
  const ctx = { contentType: file.type, fileName: file.name }
  if (isImage(ctx)) return 'image'
  if (isVideo(ctx)) return 'video'
  if (isAudio(ctx)) return 'audio'
  return 'file'
}

// 截取文件名，避免超长占满气泡
export function shortenFileName(name, maxLen = 32) {
  if (!name) return ''
  const s = String(name)
  if (s.length <= maxLen) return s
  const ext = getExtension(s)
  const head = s.slice(0, maxLen - 8 - (ext ? ext.length + 1 : 0))
  const tail = ext ? '.' + ext : ''
  return `${head}…${tail}`
}

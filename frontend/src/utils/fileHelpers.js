// D1-W9 文件辅助纯函数

const IMAGE_EXTENSIONS = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp', 'svg', 'avif', 'heic']
const VIDEO_EXTENSIONS = ['mp4', 'mov', 'webm', 'avi', 'mkv', 'm4v', '3gp']
const AUDIO_EXTENSIONS = ['mp3', 'wav', 'ogg', 'flac', 'aac', 'm4a', 'amr']
// D1-W18-02 PDF inline 预览
const PDF_EXTENSIONS = ['pdf']
// D1-W18 文本 / 代码 / 配置类文件，可在浏览器内 fetch → decode → <pre> 预览
const TEXT_EXTENSIONS = [
  'txt', 'md', 'markdown', 'log', 'csv', 'tsv', 'json', 'xml', 'yml', 'yaml', 'toml', 'ini', 'properties', 'env',
  'html', 'htm', 'css', 'scss', 'sass', 'less',
  'js', 'mjs', 'cjs', 'jsx', 'ts', 'tsx', 'vue', 'svelte',
  'java', 'kt', 'kts', 'groovy', 'scala', 'clj',
  'py', 'rb', 'go', 'rs', 'php', 'pl', 'lua', 'r',
  'c', 'cc', 'cpp', 'cxx', 'h', 'hh', 'hpp', 'hxx', 'm', 'mm', 'swift',
  'sh', 'bash', 'zsh', 'fish', 'ps1', 'bat', 'cmd',
  'sql', 'graphql', 'gql', 'proto', 'dockerfile', 'gitignore'
]
// D1-W18 Office 文件（浏览器无法本地预览，仅显示下载入口 + 友好提示）
const OFFICE_EXTENSIONS = [
  'doc', 'docx', 'dot', 'dotx', 'rtf',
  'xls', 'xlsx', 'xlsm', 'xlsb',
  'ppt', 'pptx', 'pps', 'ppsx',
  'odt', 'ods', 'odp', 'pages', 'numbers', 'key'
]

const IMAGE_PREFIXES = ['image/']
const VIDEO_PREFIXES = ['video/']
const AUDIO_PREFIXES = ['audio/']
const PDF_PREFIXES = ['application/pdf']
const TEXT_PREFIXES = ['text/']

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

// D1-W28-09 mediaType 优先：当 mediaType 显式为 image/video/audio/voice/file/composite 之一时，
// 以它为准，不再被 fileName 后缀 / contentType 反向覆盖。
// 修复场景：语音消息 mediaType='voice' + fileName='voice-xxx.webm' 以前会被 isVideo 错认（webm 在 VIDEO_EXTENSIONS），
// 导致 MediaPreview 走 video 分支 渲染 <video controls>，产生“黑色视频区 + audio 条”黑框。
function explicitMediaType(mt) {
  if (!mt) return ''
  const t = String(mt).toLowerCase()
  // 只对后端服务映射过的合法值进入优先路径，避免误伤未知 mediaType
  if (['image', 'video', 'audio', 'voice', 'file', 'composite'].includes(t)) return t
  return ''
}

export function isImage({ mediaType, fileName, contentType } = {}) {
  const mt = explicitMediaType(mediaType)
  if (mt === 'image') return true
  if (mt) return false
  if (inMime(contentType, IMAGE_PREFIXES)) return true
  if (inExt(fileName, IMAGE_EXTENSIONS)) return true
  return false
}

export function isVideo({ mediaType, fileName, contentType } = {}) {
  const mt = explicitMediaType(mediaType)
  if (mt === 'video') return true
  if (mt) return false
  if (inMime(contentType, VIDEO_PREFIXES)) return true
  if (inExt(fileName, VIDEO_EXTENSIONS)) return true
  return false
}

export function isAudio({ mediaType, fileName, contentType } = {}) {
  const mt = explicitMediaType(mediaType)
  // D1-W27-03 mediaType 兼容 'audio' / 'voice' / 大写 VOICE
  if (mt === 'audio' || mt === 'voice') return true
  if (mt) return false
  if (inMime(contentType, AUDIO_PREFIXES)) return true
  if (inExt(fileName, AUDIO_EXTENSIONS)) return true
  return false
}

// D1-W27-03 语音消息（VOICE）专属判定：与一般 audio 文件区分开，
// 用于 MediaPreview 选择紧凑播放器布局
export function isVoice({ mediaType } = {}) {
  if (!mediaType) return false
  return String(mediaType).toLowerCase() === 'voice'
}

// D1-W18-02 PDF 判定：mediaType 字段不会等于 'pdf'（后端只区分 image/video/audio/file），
// 所以仅以 contentType + 文件后缀为准
export function isPdf({ fileName, contentType } = {}) {
  if (inMime(contentType, PDF_PREFIXES)) return true
  if (inExt(fileName, PDF_EXTENSIONS)) return true
  return false
}

// D1-W18 文本类（含代码/配置）判定：能在浏览器里 fetch → decode UTF-8 → 直接预览
// 注意：必须排在 isPdf / isOffice 之后判断，避免冲突；本函数只看后缀 + text/ MIME
export function isTextLike({ fileName, contentType } = {}) {
  if (inMime(contentType, TEXT_PREFIXES)) return true
  if (inExt(fileName, TEXT_EXTENSIONS)) return true
  // 部分代码文件 MIME 不是 text/，但属于 application/json、application/xml 等
  const ct = (contentType || '').toLowerCase()
  if (ct === 'application/json' || ct === 'application/xml' || ct === 'application/javascript') return true
  return false
}

// D1-W18 Office 文档判定：浏览器无法本地预览（需要服务端 LibreOffice / Office Online），
// 仅用来在文件气泡里显示"暂不支持预览，请下载"友好提示
export function isOfficeDoc({ fileName, contentType } = {}) {
  if (inExt(fileName, OFFICE_EXTENSIONS)) return true
  const ct = (contentType || '').toLowerCase()
  if (ct.startsWith('application/vnd.ms-')) return true
  if (ct.startsWith('application/vnd.openxmlformats-officedocument')) return true
  if (ct.startsWith('application/vnd.oasis.opendocument')) return true
  if (ct === 'application/msword') return true
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

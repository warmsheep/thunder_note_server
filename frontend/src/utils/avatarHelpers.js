// D1-W11 头像 URL 工具
//
// 当前后端 file controller 上传只返回 objectName（MinIO 内部路径），
// 而 PUT /api/users/avatar 又强制 @URL 校验 avatar 字段必须是绝对 URL。
// 这里把 objectName 包进 `${origin}/api/files/download?objectName=...` 形式，
// 既能通过 @URL 校验，又能反解出 objectName 给浏览器走鉴权 fetch+blob 预览。

const DOWNLOAD_PATH = '/api/files/download'

function safeOrigin(origin) {
  if (origin) return origin
  if (typeof window !== 'undefined' && window.location && window.location.origin) {
    return window.location.origin
  }
  return 'http://localhost:8080'
}

export function buildAvatarUrl(objectName, origin) {
  if (!objectName) return ''
  const base = safeOrigin(origin)
  return `${base}${DOWNLOAD_PATH}?objectName=${encodeURIComponent(objectName)}`
}

// D1-W23-01 判断 avatar 是不是 emoji / 单字符短串（不是 URL 也不是对象名）
// 判据：
//   - 非空
//   - 不以 http(s):// 开头
//   - 不以 '/' 开头（path）
//   - 不含 '/'（避免误判 objectName "userId/xxx.jpg"）
//   - 长度 ≤ 8（覆盖组合 emoji 的最大 UTF-16 code units，如 ZWJ 序列）
export function isEmojiAvatar(avatar) {
  if (!avatar) return false
  const s = String(avatar).trim()
  if (!s) return false
  if (s.startsWith('http://') || s.startsWith('https://')) return false
  if (s.startsWith('/')) return false
  if (s.includes('/')) return false
  return s.length <= 8
}

// 反解：把 avatar 字段还原成 objectName（如果可识别），失败返回 null。
// 兼容四种形态：
// 1) emoji / 短字符串 → null（由 isEmojiAvatar 直接识别，不走对象名）
// 2) 完整下载 URL：http(s)://host/api/files/download?objectName=xxx
// 3) 仅 path：/api/files/download?objectName=xxx
// 4) 直接是 objectName（含 '/'，形如 "<userId>/<uuid>.<ext>"）
export function extractObjectName(avatar) {
  if (!avatar) return null
  const s = String(avatar).trim()
  if (!s) return null

  // 先判 emoji：isEmojiAvatar 成立时直接视为非 objectName
  if (isEmojiAvatar(s)) return null

  if (s.startsWith('http://') || s.startsWith('https://') || s.startsWith('/')) {
    try {
      const u = new URL(s, safeOrigin())
      if (u.pathname === DOWNLOAD_PATH) {
        const obj = u.searchParams.get('objectName')
        return obj || null
      }
      // 不是我们的下载 URL，可能是公开 CDN，调用方就别走 blob 路径了
      return null
    } catch (_e) {
      return null
    }
  }
  // 没有 scheme 也不是 path，且含 '/'（被 isEmojiAvatar 否决），认为本身就是 objectName
  return s
}

// 判断 avatar 字段是否需要走鉴权 fetch+blob 预览（即是我们 download endpoint 包的）
export function needsAuthenticatedFetch(avatar) {
  return extractObjectName(avatar) != null
}

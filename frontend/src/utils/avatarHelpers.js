// D1-W11 / D1-W28-17 头像字段工具
//
// 后端 user.avatar 字段现在统一存"相对资源标识"，支持四种形态：
//   1. emoji / 短字符串（如 '💼'）
//   2. objectName（如 '1/abc.png'，含 '/' 的相对路径）
//   3. 完整闪记下载 URL（历史遗留：`http(s)://host/api/files/download?objectName=...`）
//      W28-17 之后后端入库时会自动归一化为 #2 objectName，但既有数据 / 老版本客户端
//      仍可能写入这种形态，前端需要兼容。
//   4. 外链 URL（CDN / 其他图床）
// 本模块只做"形态识别 + objectName 抽取"，不再负责把 objectName 拼成绝对 URL；
// 客户端各自用本机访问的 origin 拼接，避免多域名 / 自托管部署下 host 错配。

const DOWNLOAD_PATH = '/api/files/download'

function safeOrigin(origin) {
  if (origin) return origin
  if (typeof window !== 'undefined' && window.location && window.location.origin) {
    return window.location.origin
  }
  return 'http://localhost:8080'
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

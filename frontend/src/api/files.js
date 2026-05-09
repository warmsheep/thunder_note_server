import apiClient from './client'

// D1-W9 文件上传/下载 API 封装。
// - upload：multipart/form-data，axios 会自动加正确 Content-Type 边界；onUploadProgress 透传给业务层
// - download：必须走 apiClient.raw（responseType: 'blob'）拿原始响应；自动注入 Bearer token
//   下载需鉴权（/api/files/** 在 SecurityConfig 反向规则下默认走 /api/** 鉴权）

export function uploadFile(file, { onUploadProgress } = {}) {
  if (!file) {
    return Promise.reject(new Error('file is required'))
  }
  const formData = new FormData()
  formData.append('file', file)
  return apiClient.post('/api/files/upload', formData, {
    onUploadProgress
  })
}

export async function downloadAsBlob(objectName) {
  if (!objectName) {
    throw new Error('objectName is required')
  }
  const resp = await apiClient.raw.get('/api/files/download', {
    params: { objectName },
    responseType: 'blob'
  })
  return resp.data
}

// 把 objectName 解析成可在 <img>/<video>/<audio> 直接使用的 blob: URL。
// 调用方负责在不再使用时调用 URL.revokeObjectURL 释放内存。
export async function fetchAsObjectUrl(objectName) {
  const blob = await downloadAsBlob(objectName)
  return URL.createObjectURL(blob)
}

// D1-W18 文本/代码类文件预览：fetch blob → 按 UTF-8 解码成字符串。
// 解码失败（如二进制乱入）时返回带 replacement 字符的字符串，但不抛错；
// 文件大小由调用方在 UI 层做上限保护（避免超大文件把内存吃满）。
export async function fetchAsText(objectName) {
  const blob = await downloadAsBlob(objectName)
  // Blob.text() 内部按 UTF-8 解码（无 BOM 时也能处理常见情况）
  return await blob.text()
}

// 触发浏览器下载（先 fetch 成 blob，再用临时 a[download] 触发）。
export async function triggerDownload(objectName, fileName) {
  const blob = await downloadAsBlob(objectName)
  const url = URL.createObjectURL(blob)
  try {
    const a = document.createElement('a')
    a.href = url
    a.download = fileName || 'download'
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
  } finally {
    // 微任务后释放，给浏览器写入磁盘的时间
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  }
}

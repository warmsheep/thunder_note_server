// D1-W25-03 markdown 编辑器辅助纯函数
//
// 与 Android `CardEditorFragment.insertToken / insertLinePrefix` 行为对齐：
//
// 1. applyInlineToken({text, selStart, selEnd, prefix, suffix})
//    - 选中区两侧插入；未选中时光标处插入 prefix+suffix，光标停在中间
//    - 返回 { text, selStart, selEnd }
//
// 2. toggleLinePrefix({text, selStart, selEnd, prefix})
//    - 选中区覆盖的所有行：若全部已含 prefix → 全部移除；否则全部添加
//    - 选中区为空（光标行）也按当前行处理
//    - 返回 { text, selStart, selEnd }
//
// 设计原则：
//   - 纯函数，零 DOM 依赖；调用方负责把结果回写 textarea + restore selection
//   - 字符串切片基于 UTF-16 单元（与 textarea selectionStart/End 单位一致）

function clampSel(text, value) {
  const n = text == null ? 0 : String(text).length
  const v = Number.isFinite(Number(value)) ? Math.max(0, Math.min(n, Math.floor(value))) : 0
  return v
}

export function applyInlineToken({ text = '', selStart = 0, selEnd = 0, prefix = '', suffix = '' } = {}) {
  const t = text == null ? '' : String(text)
  const start = clampSel(t, selStart)
  const end = clampSel(t, Math.max(selStart, selEnd))
  const before = t.slice(0, start)
  const selected = t.slice(start, end)
  const after = t.slice(end)
  const inserted = `${prefix}${selected}${suffix}`
  const nextText = before + inserted + after
  if (selected) {
    // 保持选区为「prefix + 原选中 + suffix」，便于再次点同一按钮取消
    return {
      text: nextText,
      selStart: start,
      selEnd: start + inserted.length
    }
  }
  // 未选中时光标停在中间（prefix 之后），方便继续输入
  const caret = start + prefix.length
  return {
    text: nextText,
    selStart: caret,
    selEnd: caret
  }
}

// 找到 [start, end] 选中区跨越的所有行的 [lineStart, lineEnd]（不含末尾换行）
function locateLineRange(text, start, end) {
  const t = String(text)
  const lineStart = t.lastIndexOf('\n', Math.max(0, start - 1)) + 1
  const nl = t.indexOf('\n', end)
  const lineEnd = nl < 0 ? t.length : nl
  return { lineStart, lineEnd }
}

export function toggleLinePrefix({ text = '', selStart = 0, selEnd = 0, prefix = '' } = {}) {
  const t = text == null ? '' : String(text)
  const p = String(prefix || '')
  if (!p) {
    return { text: t, selStart: clampSel(t, selStart), selEnd: clampSel(t, selEnd) }
  }
  const start = clampSel(t, selStart)
  const end = clampSel(t, Math.max(selStart, selEnd))
  const { lineStart, lineEnd } = locateLineRange(t, start, end)
  const block = t.slice(lineStart, lineEnd)
  const lines = block.split('\n')
  const allHavePrefix = lines.length > 0 && lines.every((line) => line.startsWith(p))
  const newLines = allHavePrefix
    ? lines.map((line) => line.slice(p.length))
    : lines.map((line) => p + line)
  const newBlock = newLines.join('\n')
  const nextText = t.slice(0, lineStart) + newBlock + t.slice(lineEnd)

  // 选区调整：每行加/减一个 prefix；位置落在第 k 行（0-indexed）就累加 (k+1) * delta
  // - 行首（pos === lineStart 或 pos === 各行起点）也按"落在该行"算，与用户期望
  //   "光标整体随 prefix 一起移动" 一致
  const delta = allHavePrefix ? -p.length : p.length
  function adjust(pos) {
    // pos 严格在 block 之前 → 不动
    if (pos < lineStart) return pos
    // pos 在 block 之后 → 整体偏移 newBlock - block
    if (pos > lineEnd) {
      return pos + (newBlock.length - block.length)
    }
    // pos 落在 block 内（含 lineStart）：算 pos 在第几行内
    const rel = pos - lineStart
    let consumed = 0
    for (let i = 0; i < lines.length; i++) {
      const lineLen = lines[i].length
      const lineSpan = lineLen + 1 // +1 是换行符（最后一行没有换行也按 +1 简化）
      if (rel <= consumed + lineLen) {
        return pos + (i + 1) * delta
      }
      consumed += lineSpan
    }
    return pos + lines.length * delta
  }

  return {
    text: nextText,
    selStart: clampSel(nextText, adjust(start)),
    selEnd: clampSel(nextText, adjust(end))
  }
}

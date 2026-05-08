// D1-W7 合集与闪记关联辅助
// 当前后端模型：flashNote.tags 是字符串，等于其所属 collection.name；
// inbox=true 的收集箱闪记不参与合集分组（即使其 tags 字符串恰好命中合集名，UI 也单独展示）。

const COLLECTION_NAME_MAX_LENGTH = 255
const UNCATEGORIZED_KEY = '__uncategorized__'

export { UNCATEGORIZED_KEY }

export function validateCollectionName(value, { required = true } = {}) {
  const v = (value || '').trim()
  if (!v) {
    if (required) return { ok: false, message: '请输入合集名称' }
    return { ok: true }
  }
  if (v.length > COLLECTION_NAME_MAX_LENGTH) {
    return { ok: false, message: `合集名称不能超过 ${COLLECTION_NAME_MAX_LENGTH} 个字符` }
  }
  return { ok: true }
}

export function validateCollectionForm(form, { required = true } = {}) {
  return validateCollectionName(form && form.name, { required })
}

// 把闪记按其 tags 字符串归到对应 collection 名下；返回 { byCollectionId: Map, uncategorized: [] }。
// 不计入 inbox 闪记（收集箱）。
export function groupNotesByCollection(notes, collections) {
  const result = {
    byCollectionId: new Map(),
    uncategorized: []
  }
  if (!Array.isArray(collections)) return result
  // 按 name → collectionId 建索引（同名取第一个，后端 service 通常会防重）
  const nameToId = new Map()
  for (const c of collections) {
    if (!c || c.id == null || c.name == null) continue
    if (!nameToId.has(c.name)) {
      nameToId.set(c.name, c.id)
    }
    if (!result.byCollectionId.has(c.id)) {
      result.byCollectionId.set(c.id, [])
    }
  }
  if (!Array.isArray(notes)) return result
  for (const n of notes) {
    if (!n) continue
    if (n.inbox) continue
    if (n.deleted) continue
    const tag = (n.tags || '').trim()
    if (tag && nameToId.has(tag)) {
      result.byCollectionId.get(nameToId.get(tag)).push(n)
    } else {
      result.uncategorized.push(n)
    }
  }
  return result
}

export function countNotesInCollection(notes, collectionName) {
  if (!Array.isArray(notes) || !collectionName) return 0
  let count = 0
  for (const n of notes) {
    if (!n || n.inbox || n.deleted) continue
    if ((n.tags || '').trim() === collectionName) count += 1
  }
  return count
}

export function listNotesInCollection(notes, collectionName) {
  if (!Array.isArray(notes) || !collectionName) return []
  return notes.filter(
    (n) => n && !n.inbox && !n.deleted && (n.tags || '').trim() === collectionName
  )
}

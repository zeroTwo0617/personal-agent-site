import { marked } from 'marked'
import DOMPurify from 'dompurify'

// 聊天消息 Markdown 渲染:
// 1) [N] 引用 → <sup class="cite">N</sup>(人设要求末尾标注来源,渲染成角标)
// 2) marked 渲染 Markdown(支持 **粗体**、列表、代码等)
// 3) DOMPurify 消毒,防止注入
export function renderMd(text: string): string {
  if (!text) return ''
  const withCites = text.replace(/\[(\d+)\]/g, '<sup class="cite">$1</sup>')
  const raw = marked.parse(withCites, { breaks: true, gfm: true }) as string
  return DOMPurify.sanitize(raw)
}

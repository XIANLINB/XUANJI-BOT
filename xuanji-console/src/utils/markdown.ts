// 轻量 Markdown 渲染（复用项目内已存在的 markdown-it 依赖）。
// html:false 使输入中的原生 HTML 被转义，避免 XSS。
import MarkdownIt from 'markdown-it'

const md = new MarkdownIt({ html: false, linkify: true, breaks: true })
// 文档类渲染：换行不强制转 <br>（表格/代码块/段落排版用）
const mdDoc = new MarkdownIt({ html: false, linkify: true, breaks: false })

export function renderMarkdown(text: string, opts?: { breaks?: boolean }): string {
  return (opts?.breaks === false ? mdDoc : md).render(text || '')
}

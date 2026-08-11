// 消息监控 · 会话聊天 API（主动发送 / 富媒体发送 / 会话消息）
import { get, post } from './http'

export const chatApi = {
  /** 主动发送文本/Markdown：msgType = text | markdown */
  send: (bot: string, targetType: 'group' | 'c2c', targetId: string, msgType: string, content: string) =>
    post('/console/chat/send', { bot, targetType, targetId, msgType, content }),
  /** 富媒体发送：base64 为 dataURL 或纯 base64；fileType 1=图片 2=视频 3=语音 */
  sendMedia: (bot: string, targetType: 'group' | 'c2c', targetId: string,
               fileType: number, base64: string, filename: string) =>
    post('/console/chat/send-media', { bot, targetType, targetId, fileType, base64, filename }),
  /** 单会话历史消息（qqbot 模块，倒序取最近 limit 条转正序） */
  chatMessages: (bot: string, targetType: 'group' | 'c2c', targetId: string, limit = 100) =>
    get('/console/chat/messages', { bot, targetType, targetId, limit })
}

export type ChatMsgType = 'text' | 'markdown' | 'image' | 'video' | 'voice'
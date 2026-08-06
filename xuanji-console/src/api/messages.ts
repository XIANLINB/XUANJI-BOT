// 消息监控 / 系统事件 API
import { get } from './http'

export const messagesApi = {
  getMessages: (chat: 'group' | 'c2c', bot = '', page = 0, size = 200) =>
    get(`/console/${chat === 'group' ? 'group' : 'c2c'}-messages`, { bot, page, size }),
  getEvents: (bot = '', limit = 200) => get('/console/event-log', { bot, limit })
}

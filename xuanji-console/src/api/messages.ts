// 消息监控 / 系统事件 / 消息趋势 API
import { get } from './http'

export const messagesApi = {
  getMessages: (chat: 'group' | 'c2c', bot = '', page = 0, size = 200) =>
    get(`/console/${chat === 'group' ? 'group' : 'c2c'}-messages`, { bot, page, size }),
  getEvents: (bot = '', limit = 200) => get('/console/event-log', { bot, limit }),
  /** 消息趋势（仪表盘）：近 days 天单聊/群聊/总消息量。 */
  getMessageTrend: (days = 7, bot = '') => get('/console/message-trend', { days, bot })
}

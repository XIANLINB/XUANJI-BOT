// 消息监控 / 系统事件 / 消息趋势 API
import { get, post } from './http'

export const messagesApi = {
  getMessages: (chat: 'group' | 'c2c', bot = '', page = 0, size = 200) =>
    get(`/console/${chat === 'group' ? 'group' : 'c2c'}-messages`, { bot, page, size }),
  getEvents: (bot = '', limit = 200) => get('/console/event-log', { bot, limit }),
  /** 消息趋势（仪表盘）：近 days 天单聊/群聊/总消息量。 */
  getMessageTrend: (days = 7, bot = '') => get('/console/message-trend', { days, bot }),
  /** 撤回群消息（后端：机器人自己消息免角色校验；他人消息需本群管理员）。 */
  recallGroupMessage: (body: { appId: string; groupOpenid: string; msgId: string; isOwn?: boolean }) =>
    post('/console/group-messages/recall', body)
}

// 消息监控 / 系统事件 / 消息趋势 API
import { get, post } from './http'

export const messagesApi = {
  getMessages: (chat: 'group' | 'c2c', bot = '', page = 0, size = 200) =>
    get(`/console/${chat === 'group' ? 'group' : 'c2c'}-messages`, { bot, page, size }),
  /** 群消息（服务端筛选 + 分页）：返回 { rows, total, ins, outs, typeDist }。 */
  getGroupMessages: (params: { bot?: string; page?: number; size?: number; dir?: string; type?: string; startTime?: number; endTime?: number; q?: string }) =>
    get('/console/group-messages', { ...params }),
  /** 单聊消息（服务端筛选 + 分页）：返回 { rows, total, ins, outs, typeDist }。 */
  getC2cMessages: (params: { bot?: string; page?: number; size?: number; dir?: string; type?: string; startTime?: number; endTime?: number; q?: string }) =>
    get('/console/c2c-messages', { ...params }),
  getEvents: (bot = '', limit = 200) => get('/console/event-log', { bot, limit }),
  /** 管理操作日志（禁言/撤回/审批等出站审计，含失败与被拒记录）。 */
  getOpLogs: (q: { bot?: string; opType?: string; status?: string; groupId?: string; keyword?: string; limit?: number } = {}) =>
    get('/console/op-log', {
      bot: q.bot || '', opType: q.opType || '', status: q.status || '',
      groupId: q.groupId || '', keyword: q.keyword || '', limit: q.limit || 200
    }),
  /** 消息趋势（仪表盘）：近 days 天单聊/群聊/总消息量。 */
  getMessageTrend: (days = 7, bot = '') => get('/console/message-trend', { days, bot }),
  /** 撤回群消息（后端：机器人自己消息免角色校验；他人消息需本群管理员）。 */
  recallGroupMessage: (body: { appId: string; groupOpenid: string; msgId: string; isOwn?: boolean }) =>
    post('/console/group-messages/recall', body)
}

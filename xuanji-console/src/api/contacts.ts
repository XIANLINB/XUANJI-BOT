// 联系人 / 会话 API
import { get, post } from './http'

export interface ContactMessagesParams {
  type: 'group' | 'c2c'
  targetId: string
  startTime?: number
  endTime?: number
  beforeTime?: number
  limit?: number
}

export const contactsApi = {
  getGroups: () => get('/console/contacts/groups'),
  /** 群列表分页（Q11）：返回 { rows, total, notDeleted, deleted, memberSum }。 */
  getGroupsPage: (params: { page: number; size: number; bot?: string; q?: string; showDeleted?: boolean }) =>
    get('/console/contacts/groups-page', { ...params }),
  getFriends: () => get('/console/contacts/friends'),
  /** 单聊用户列表分页：返回 { rows, total, notDeleted, deleted }。 */
  getFriendsPage: (params: { page: number; size: number; bot?: string; q?: string; showDeleted?: boolean }) =>
    get('/console/contacts/friends-page', { ...params }),
  getGroupMembers: (groupId: string, bot?: string) =>
    get('/console/contacts/group-members', { groupId, bot }),
  /** 某群内各机器人的群内状态（跨所有 bot 聚合）。 */
  getGroupRobotStates: (groupOpenid: string) =>
    get('/console/contacts/group-robot-states', { groupOpenid }),
  /** 单会话消息（控制台 · 聊天监控）：返回 { rows: [...], hasMore: bool }，rows 按 create_time 升序。 */
  getContactMessages: (params: ContactMessagesParams) =>
    get('/console/contact-messages', { ...params }),
  /** 批量会话最新预览（Q4）：一次返回多个会话各最新一条，替代逐会话轮询。 */
  getContactPreviews: (targets: { bot: string; type: string; targetId: string }[]) =>
    post('/console/contact-previews', { targets })
}

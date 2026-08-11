// 联系人 / 会话 API
import { get } from './http'

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
  getFriends: () => get('/console/contacts/friends'),
  getGroupMembers: (groupId: string) => get('/console/contacts/group-members', { groupId }),
  /** 某群内各机器人的群内状态（跨所有 bot 聚合）。 */
  getGroupRobotStates: (groupOpenid: string) =>
    get('/console/contacts/group-robot-states', { groupOpenid }),
  /** 单会话消息（控制台 · 聊天监控）：返回 { rows: [...], hasMore: bool }，rows 按 create_time 升序。 */
  getContactMessages: (params: ContactMessagesParams) =>
    get('/console/contact-messages', { ...params })
}

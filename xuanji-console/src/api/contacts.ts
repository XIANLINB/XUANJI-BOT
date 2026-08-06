// 联系人 / 会话 API
import { get } from './http'

export const contactsApi = {
  getGroups: () => get('/console/contacts/groups'),
  getFriends: () => get('/console/contacts/friends'),
  getGroupMembers: (groupId: string) => get('/console/contacts/group-members', { groupId }),
  getContactMessages: (type: string, targetId: string) =>
    get('/console/contact-messages', { type, targetId })
}

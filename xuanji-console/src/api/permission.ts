// 权限管理 API（主人 / 黑名单）
import { get, post, del, qs } from './http'

export const permissionApi = {
  // 主人
  getOwner: (botKey: string) => get('/console/permission/owner', { botKey }),
  setOwner: (botKey: string, ownerOpenid: string) =>
    post('/console/permission/owner' + qs({ botKey, ownerOpenid })),
  clearOwner: (botKey: string) => del('/console/permission/owner' + qs({ botKey })),
  // 黑名单
  listBlacklist: (botKey: string) => get('/console/permission/blacklist', { botKey }),
  addBlacklist: (botKey: string, groupId: string, userId: string, reason?: string) =>
    post('/console/permission/blacklist' + qs({ botKey, groupId, userId, reason })),
  // 按 id 删除最稳（空群黑名单 group_id='' 会被 qs 过滤掉导致 WHERE 匹配不到，按 id 不受影响）
  removeBlacklistById: (id: number | string) => del('/console/permission/blacklist' + qs({ id })),
  removeBlacklist: (botKey: string, groupId: string, userId: string) =>
    del('/console/permission/blacklist' + qs({ botKey, groupId, userId }))
}

// 显示名兜底工具：群名称 / 用户名称缺失时用对应 ID 前 8 位替代，仍无则返回"未知"。
// 全局规则（2026-08-07 用户要求）：有名称显示名称，没有就显示 ID 前 8 位。
//
// 兼容的字段名（按优先级）：
// - 群名：GROUP_NAME / groupName / GNAME / name / group_name
// - 群 ID：GROUP_ID / groupId / GID / gid / id / groupOpenid / group_id
// - 昵称：NICKNAME / nickname / NICK / nickName / REMARK / remark / name / nick
// - 用户 ID：USER_ID / UID / userId / user_id / id / PLATFORM_USER_ID / openid / memberOpenid

/** 取行的任意字段（大小写兼容），无则空串。 */
function pick(row: any, keys: string[]): string {
  if (!row) return ''
  for (const k of keys) {
    const v = row[k]
    if (v != null && String(v).trim() !== '') return String(v).trim()
  }
  return ''
}

/**
 * 群显示名：优先群名称，缺失用群 ID 前 8 位兜底。
 */
export function groupName(row: any): string {
  const name = pick(row, ['GROUP_NAME', 'groupName', 'GNAME', 'name', 'group_name'])
  if (name) return name
  const id = pick(row, ['GROUP_ID', 'groupId', 'GID', 'gid', 'id', 'groupOpenid', 'group_id'])
  if (id) return id.slice(0, 8)
  return '未知'
}

/**
 * 用户显示名：优先昵称，缺失用用户 ID 前 8 位兜底。
 */
export function userName(row: any): string {
  const name = pick(row, ['NICKNAME', 'nickname', 'NICK', 'nickName', 'REMARK', 'remark', 'name', 'nick'])
  if (name) return name
  const id = pick(row, ['USER_ID', 'UID', 'userId', 'user_id', 'id', 'PLATFORM_USER_ID', 'openid', 'memberOpenid', 'UNION_OPENID', 'unionOpenid'])
  if (id) return id.slice(0, 8)
  return '未知'
}
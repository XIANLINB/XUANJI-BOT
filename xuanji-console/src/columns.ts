// 数据库列名 / 表头 → 中文映射。
// H2 默认把未加引号的列名转为大写返回（GROUP_ID），故统一以小写查找。
const COL_MAP: Record<string, string> = {
  // 通用
  id: 'ID',
  platform: '平台',
  status: '状态',
  adapter: '适配器',
  created_at: '创建时间',
  updated_at: '更新时间',

  // xuanji_bot
  instance_id: '实例ID',

  // xuanji_kv
  k: '键',
  scope: '作用域',
  v: '值',

  // xuanji_dedup
  event_id: '事件ID',

  // xuanji_plugin
  plugin_key: '插件标识',
  name: '名称',
  platforms: '适用平台',
  version: '版本',
  author: '作者',
  description: '描述',

  // xuanji_plugin_state
  plugin_id: '插件ID',
  enabled: '启用',

  // xuanji_setup
  admin_password: '管理员密码',
  step: '向导步骤',

  // xlog_framework
  level: '级别',
  module: '模块',
  message: '消息',

  // qqbot_bot
  bot_appid: '应用ID',
  bot_clientsecret: '客户端密钥',
  conn_mode: '连接模式',

  // qqbot_botinfo
  botid: '机器人ID(内部)',
  bot_id: '机器人ID',
  avatar: '头像',
  is_bot: '是否机器人',
  union_openid: 'UnionID',
  share_url: '分享链接',
  welcome_msg: '欢迎语',

  // qqbot_group
  group_id: '群号',
  group_name: '群名称',
  owner_id: '群主ID',
  member_count: '成员数',
  join_time: '加入时间',
  is_deleted: '已删除',

  // qqbot_group_member
  member_id: '成员ID',
  role: '角色',
  nickname: '昵称',

  // qqbot_user
  platform_user_id: '平台用户ID',
  remark: '备注',

  // qqbot_message
  chat_type: '聊天类型',
  user_id: '用户ID',
  direction: '方向',
  msg_type: '消息类型',
  content: '内容',
  msg_id: '消息ID',
  msg_seq: '消息序号',
  raw_json: '原始数据',

  // qqbot_event
  event_type: '事件类型',

  // 实时事件流（/console/events，camelCase）
  time: '时间',
  type: '类型',
  user: '用户',
  groupid: '群号',
  detail: '详情'
}

/** 列名 → 中文表头；未收录的列原样返回。 */
export function colLabel(key: unknown): string {
  if (key == null) return ''
  const k = String(key).toLowerCase()
  return COL_MAP[k] ?? String(key)
}

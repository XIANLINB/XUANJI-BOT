// 璇玑控制台前端 API 客户端。
// 按域拆分（bots / contacts / messages / monitor / plugins / permission / db / logs / setup），
// 本文件聚合导出，各视图保持 `import api from '../api'` 用法不变。
import { botsApi } from './bots'
import { contactsApi } from './contacts'
import { messagesApi } from './messages'
import { monitorApi } from './monitor'
import { pluginsApi } from './plugins'
import { permissionApi } from './permission'
import { dbApi } from './db'
import { logsApi } from './logs'
import { setupApi } from './setup'
import { authApi } from './auth'
import { securityApi } from './security'
import { backupApi } from './backup'
import { schedulerApi } from './scheduler'
import { dataApi } from './data'
import { alertApi } from './alert'
import { riskApi } from './risk'
import { systemApi } from './system'
import { chatApi } from './chat'
import { llmApi } from './llm'
import { getActuatorMetric } from './http'

export default {
  ...botsApi,
  ...contactsApi,
  ...messagesApi,
  ...monitorApi,
  ...pluginsApi,
  ...permissionApi,
  ...dbApi,
  ...logsApi,
  ...setupApi,
  ...authApi,
  ...securityApi,
  ...backupApi,
  ...schedulerApi,
  ...dataApi,
  ...alertApi,
  ...riskApi,
  ...chatApi,
  llmApi,
  systemApi,
  getActuatorMetric
}

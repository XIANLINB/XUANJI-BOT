// 运行日志 API
import { getText } from './http'

export const logsApi = {
  getLogs: (lines = 100) => getText('/console/logs', { lines }) // 后端返回纯文本日志
}

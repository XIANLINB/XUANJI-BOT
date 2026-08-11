// 系统控制 API（重启 / 启动脚本）
import { post, get, getText } from './http'

export const systemApi = {
  /** 一键重启：body.confirm 必须为 "RESTART" 才生效 */
  restart: (confirm: string) => post('/console/system/restart', { confirm }),
  /** 获取标准 start.sh 脚本内容（后端返回 text/plain，必须走 getText） */
  getStartScript: () => getText('/console/system/start-script'),
  /** 写入标准 start.sh 到工作目录 */
  writeStartScript: () => post('/console/system/start-script/write', {})
}
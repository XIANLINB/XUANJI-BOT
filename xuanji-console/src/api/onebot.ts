// OneBot 适配器状态 API
import { get } from './http'

export const onebotApi = {
  getStatus: () => get('/onebot/status')
}

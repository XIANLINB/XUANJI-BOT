// 风控中心 API
import { get } from './http'

export const riskApi = {
  riskOverview: () => get('/console/risk/overview'),
  riskGroups: () => get('/console/risk/groups'),
  riskTimeline: (botKey = '', limit = 100) => get('/console/risk/blacklist-timeline', { botKey, limit })
}

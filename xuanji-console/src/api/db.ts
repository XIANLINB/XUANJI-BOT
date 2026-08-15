// 数据库浏览器 API
import { get } from './http'

export const dbApi = {
  dbTables: () => get('/db/tables'),
  dbRows: (table: string, source = '') => get('/db/rows', { table, source }),
  dbFilter: (params: { table: string; source?: string; column?: string; op?: string; value?: string; orderBy?: string; orderDir?: string }) =>
    get('/db/filter', params)
}

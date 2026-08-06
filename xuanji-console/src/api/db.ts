// 数据库浏览器 API
import { get } from './http'

export const dbApi = {
  dbTables: () => get('/db/tables'),
  dbRows: (table: string, source = '') => get('/db/rows', { table, source }),
  dbQuery: (sql: string, source = '') => get('/db/query', { sql, source })
}

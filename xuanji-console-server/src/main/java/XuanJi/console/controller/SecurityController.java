package XuanJi.console.controller;

import XuanJi.console.service.AuditService;
import XuanJi.core.security.PinCrypto;
import XuanJi.core.web.XuanJiApi;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 控制台 · 安全中心：修改访问口令 + 审计日志。
 *
 * <p>单用户场景，不含多会话管理；会话登出走既有 {@code /auth/logout}。
 */
@XuanJiApi
@RestController
@RequestMapping("/console/security")
public class SecurityController {

    private final JdbcTemplate jdbc;
    private final AuditService auditService;

    public SecurityController(JdbcTemplate jdbc, AuditService auditService) {
        this.jdbc = jdbc;
        this.auditService = auditService;
    }

    /** 修改访问口令：body {oldPin, newPin}。校验旧口令后覆盖（PBKDF2 新盐）。 */
    @PostMapping("/pin")
    public Map<String, Object> changePin(@RequestBody Map<String, String> body, HttpServletRequest req) {
        String oldPin = body.get("oldPin");
        String newPin = body.get("newPin");
        if (newPin == null || !newPin.matches("\\d{6}")) {
            return Map.of("error", "新口令必须是 6 位数字");
        }
        // 校验旧口令
        try {
            String salt = jdbc.queryForObject("SELECT pin_salt FROM xuanji_setup WHERE id=1", String.class);
            String hash = jdbc.queryForObject("SELECT pin_hash FROM xuanji_setup WHERE id=1", String.class);
            if (salt == null || hash == null || !PinCrypto.verify(oldPin == null ? "" : oldPin, salt, hash)) {
                auditService.record("CHANGE_PIN_FAIL", "旧口令校验失败", req);
                return Map.of("error", "当前口令不正确");
            }
        } catch (Exception e) {
            return Map.of("error", "读取口令配置失败");
        }
        // 覆盖新口令
        String newSalt = PinCrypto.generateSalt();
        String newHash = PinCrypto.hashPin(newPin, newSalt);
        jdbc.update("UPDATE xuanji_setup SET pin_salt=?, pin_hash=? WHERE id=1", newSalt, newHash);
        auditService.record("CHANGE_PIN", "访问口令已修改", req);
        return Map.of("status", "ok");
    }

    /** 审计日志（支持筛选：action / ip / keyword / deviceType / startTime / endTime，倒序）。 */
    @GetMapping("/audit")
    public Map<String, Object> audit(@RequestParam(defaultValue = "200") int limit,
                                     @RequestParam(required = false) String action,
                                     @RequestParam(required = false) String ip,
                                     @RequestParam(required = false) String keyword,
                                     @RequestParam(required = false) String deviceType,
                                     @RequestParam(required = false) Long startTime,
                                     @RequestParam(required = false) Long endTime) {
        List<Map<String, Object>> rows = auditService.list(limit, action, ip, keyword, startTime, endTime, deviceType);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rows", rows);
        m.put("count", rows.size());
        return m;
    }

    /** 审计日志导出：format=csv|json，支持与列表相同筛选（最多 1 万条）。 */
    @GetMapping("/audit/export")
    public ResponseEntity<byte[]> exportAudit(@RequestParam(defaultValue = "csv") String format,
                                              @RequestParam(required = false) String action,
                                              @RequestParam(required = false) String ip,
                                              @RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) String deviceType,
                                              @RequestParam(required = false) Long startTime,
                                              @RequestParam(required = false) Long endTime) {
        List<Map<String, Object>> rows = auditService.list(10_000, action, ip, keyword, startTime, endTime, deviceType);
        String stamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new java.util.Date());
        String filename = "audit-" + stamp + "." + format;
        byte[] body;
        String contentType;
        if ("json".equalsIgnoreCase(format)) {
            body = toJson(rows);
            contentType = "application/json; charset=UTF-8";
        } else {
            body = toCsv(rows);
            contentType = "text/csv; charset=UTF-8";
        }
        String disposition = "attachment; filename=\"" + filename + "\"";
        return ResponseEntity.ok()
                .header("Content-Disposition", disposition)
                .contentType(org.springframework.http.MediaType.parseMediaType(contentType))
                .body(body);
    }

    /** 审计日志可筛选的动作清单（前端下拉用）。 */
    @GetMapping("/audit/actions")
    public List<Map<String, Object>> auditActions() {
        return auditService.actionStats();
    }

    /** 清空审计日志。 */
    @PostMapping("/audit/clear")
    public Map<String, Object> clearAudit(HttpServletRequest req) {
        int n = auditService.clear();
        auditService.record("AUDIT_CLEAR", "清空审计日志 " + n + " 条", req);
        return Map.of("status", "ok", "cleared", n);
    }

    // ═══════════════════ 导出序列化 ═══════════════════

    private static byte[] toJson(List<Map<String, Object>> rows) {
        try {
            return new tools.jackson.databind.ObjectMapper()
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(rows);
        } catch (Exception e) {
            return "[]".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    /** CSV：UTF-8 BOM（Excel 直接打开中文不乱码）+ 表头 + 转义行。 */
    private static byte[] toCsv(List<Map<String, Object>> rows) {
        StringBuilder sb = new StringBuilder("\uFEFF"); // UTF-8 BOM
        sb.append("时间,动作,详情,IP,设备类型,操作系统,浏览器\r\n");
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        for (Map<String, Object> r : rows) {
            long ts = r.get("CREATE_TIME") instanceof Number n ? n.longValue() : 0L;
            String time = ts > 0 ? sdf.format(new java.util.Date(ts * 1000)) : "";
            sb.append(csv(time)).append(',')
              .append(csv(String.valueOf(r.getOrDefault("ACTION", "")))).append(',')
              .append(csv(String.valueOf(r.getOrDefault("DETAIL", "")))).append(',')
              .append(csv(String.valueOf(r.getOrDefault("IP", "")))).append(',')
              .append(csv(String.valueOf(r.getOrDefault("DEVICE_TYPE", "")))).append(',')
              .append(csv(String.valueOf(r.getOrDefault("DEVICE_OS", "")))).append(',')
              .append(csv(String.valueOf(r.getOrDefault("DEVICE_BROWSER", "")))).append("\r\n");
        }
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /** CSV 字段转义：含逗号/引号/换行时双引号包裹，内部引号翻倍。 */
    private static String csv(String v) {
        if (v == null) return "";
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}

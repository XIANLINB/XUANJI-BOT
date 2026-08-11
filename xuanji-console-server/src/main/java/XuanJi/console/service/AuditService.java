package XuanJi.console.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 审计日志服务 — 敏感操作留痕（登录/登出/改口令/SQL 执行/备份恢复/插件卸载/机器人启停/缓存清理等）。
 *
 * <p>落框架库 {@code xuanji_audit} 表（安全中心/操作日志页展示）；写入失败静默降级，不影响主流程。
 * <p>设备字段（device_type/device_os/device_browser）从请求 User-Agent 解析。
 */
@Slf4j
@Service
public class AuditService {

    private final JdbcTemplate jdbc;

    public AuditService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 记录一条审计（无 HttpServletRequest：设备信息留空，ip 显式传入）。 */
    public void record(String action, String detail, String ip) {
        record(action, detail, ip, "", "", "");
    }

    /** 记录一条审计（自动从请求解析 IP + 设备信息）。 */
    public void record(String action, String detail, HttpServletRequest req) {
        String ip = ip(req);
        DeviceInfo d = parseDeviceInfo(req.getHeader("User-Agent"));
        record(action, detail, ip, d.type, d.os, d.browser);
    }

    /** 记录一条审计（全字段显式）。action 用英文短码（LOGIN_OK/CHANGE_PIN/BOT_START/...）。 */
    public void record(String action, String detail, String ip,
                       String deviceType, String deviceOs, String deviceBrowser) {
        try {
            jdbc.update("""
                INSERT INTO xuanji_audit (action, detail, ip, device_type, device_os, device_browser, create_time)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """, action, truncate(detail), ip, nvl(deviceType), nvl(deviceOs), nvl(deviceBrowser),
                    System.currentTimeMillis() / 1000L);
        } catch (Exception e) {
            log.debug("[Audit] 写入失败（可忽略）: {}", e.getMessage());
        }
    }

    /** 查询审计（倒序，支持筛选：动作 / IP / 详情关键词 / 时间范围 [startTime, endTime] epoch 秒 / 设备类型）。 */
    public List<Map<String, Object>> list(int limit, String action, String ip, String keyword,
                                          Long startTime, Long endTime, String deviceType) {
        try {
            StringBuilder sql = new StringBuilder(
                    "SELECT id, action, detail, ip, device_type, device_os, device_browser, create_time FROM xuanji_audit WHERE 1=1");
            List<Object> args = new ArrayList<>();
            if (action != null && !action.isBlank()) {
                sql.append(" AND action=?");
                args.add(action.trim());
            }
            if (ip != null && !ip.isBlank()) {
                sql.append(" AND ip LIKE ?");
                args.add("%" + ip.trim() + "%");
            }
            if (keyword != null && !keyword.isBlank()) {
                sql.append(" AND detail LIKE ?");
                args.add("%" + keyword.trim() + "%");
            }
            if (deviceType != null && !deviceType.isBlank()) {
                sql.append(" AND device_type=?");
                args.add(deviceType.trim());
            }
            if (startTime != null && startTime > 0) {
                sql.append(" AND create_time>=?");
                args.add(startTime);
            }
            if (endTime != null && endTime > 0) {
                sql.append(" AND create_time<=?");
                args.add(endTime);
            }
            sql.append(" ORDER BY id DESC LIMIT ?");
            args.add(Math.min(Math.max(limit, 1), 10_000));
            return jdbc.queryForList(sql.toString(), args.toArray());
        } catch (Exception e) {
            log.debug("[Audit] 查询失败: {}", e.getMessage());
            return List.of();
        }
    }

    /** 全部动作类型（用于筛选下拉）。 */
    public List<Map<String, Object>> actionStats() {
        try {
            return jdbc.queryForList(
                    "SELECT action, COUNT(*) AS cnt FROM xuanji_audit GROUP BY action ORDER BY cnt DESC");
        } catch (Exception e) {
            log.debug("[Audit] actionStats 失败: {}", e.getMessage());
            return List.of();
        }
    }

    /** 清空审计日志（返回删除行数）。 */
    public int clear() {
        try {
            return jdbc.update("DELETE FROM xuanji_audit");
        } catch (Exception e) {
            log.debug("[Audit] 清空失败: {}", e.getMessage());
            return 0;
        }
    }

    // ═══════════════════ 工具 ═══════════════════

    /** 从请求头取客户端 IP（穿透 X-Forwarded-For / X-Real-IP）。 */
    public static String ip(HttpServletRequest req) {
        if (req == null) return "";
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        String real = req.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) return real.trim();
        return req.getRemoteAddr();
    }

    /** UA 解析结果。 */
    public record DeviceInfo(String type, String os, String browser) {}

    /** 从 User-Agent 解析设备类型 / 操作系统 / 浏览器。未知一律 unknown。 */
    public static DeviceInfo parseDeviceInfo(String ua) {
        if (ua == null || ua.isBlank()) return new DeviceInfo("unknown", "unknown", "unknown");
        String s = ua.toLowerCase();

        // 设备类型：机器人 > 平板 > 手机 > PC
        String type;
        if (s.contains("qqbot") || s.contains("bot/") || s.contains("curl") || s.contains("httpclient")
                || s.contains("python-requests") || s.contains("java/") || s.contains("okhttp")) {
            type = "bot";
        } else if (s.contains("ipad") || s.contains("tablet") || s.contains("kindle")) {
            type = "tablet";
        } else if (s.contains("mobile") || s.contains("android") || s.contains("iphone")
                || s.contains("ipod") || s.contains("windows phone") || s.contains("harmonyos")) {
            type = "mobile";
        } else {
            type = "desktop";
        }

        // 操作系统
        String os;
        if (s.contains("windows")) os = "windows";
        else if (s.contains("mac os") || s.contains("macintosh") || s.contains("darwin")) os = "macos";
        else if (s.contains("linux")) os = "linux";
        else if (s.contains("android")) os = "android";
        else if (s.contains("iphone") || s.contains("ipad") || s.contains("ios")) os = "ios";
        else if (s.contains("harmonyos")) os = "harmonyos";
        else if (s.contains("windows phone")) os = "windowsphone";
        else os = "unknown";

        // 浏览器
        String browser;
        if (s.contains("edg/")) browser = "edge";
        else if (s.contains("chrome")) browser = "chrome";
        else if (s.contains("safari")) browser = "safari";
        else if (s.contains("firefox")) browser = "firefox";
        else if (s.contains("opera") || s.contains("opr/")) browser = "opera";
        else if (s.contains("ie") || s.contains("trident")) browser = "ie";
        else if (s.contains("micromessenger") || s.contains("wechat")) browser = "wechat";
        else browser = "unknown";

        return new DeviceInfo(type, os, browser);
    }

    /** 兼容旧表：查询时设备列可能不存在（表尚未 ALTER）。 */
    public boolean hasDeviceColumn() {
        try {
            return jdbc.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME='XUANJI_AUDIT' AND COLUMN_NAME='DEVICE_TYPE'",
                    Integer.class) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() > 1024 ? s.substring(0, 1024) : s;
    }
}

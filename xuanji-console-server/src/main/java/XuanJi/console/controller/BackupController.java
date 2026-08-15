package XuanJi.console.controller;

import XuanJi.console.service.BackupService;
import XuanJi.core.web.XuanJiApi;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 控制台 · 备份恢复：立即备份 / 列表 / 下载 / 恢复 / 删除 / 自动备份设置。
 */
@XuanJiApi
@RestController
@RequestMapping("/console/backup")
public class BackupController {

    private final BackupService backupService;

    public BackupController(BackupService backupService) {
        this.backupService = backupService;
    }

    /** 立即备份。categories 逗号分隔（framework/platform/business/logs/messages，缺省全选）；tag 用于文件名区分（如 msg）。 */
    @PostMapping("/create")
    public Map<String, Object> create(@RequestParam(required = false) String categories,
                                      @RequestParam(defaultValue = "") String tag,
                                      HttpServletRequest req) {
        List<String> cats = (categories == null || categories.isBlank())
                ? java.util.List.of()
                : java.util.Arrays.stream(categories.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).toList();
        String name = backupService.create(cats, tag, ip(req));
        return Map.of("status", "ok", "name", name);
    }

    /** 备份列表（文件名/大小/时间，倒序）。 */
    @GetMapping("/list")
    public List<Map<String, Object>> list() {
        return backupService.list();
    }

    /** 下载备份 zip。 */
    @GetMapping("/download")
    public ResponseEntity<byte[]> download(@RequestParam String name) {
        Path p = backupService.resolve(name);
        if (p == null) return ResponseEntity.notFound().build();
        try {
            byte[] data = Files.readAllBytes(p);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(data);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /** 恢复备份（先自动快照当前库，再逐个库导入；建议恢复后重启）。 */
    @PostMapping("/restore")
    public Map<String, Object> restore(@RequestParam String name, HttpServletRequest req) {
        String message = backupService.restore(name, ip(req));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", "ok");
        m.put("message", message);
        return m;
    }

    /** 删除备份文件。 */
    @PostMapping("/delete")
    public Map<String, Object> delete(@RequestParam String name, HttpServletRequest req) {
        boolean ok = backupService.delete(name, ip(req));
        return Map.of("status", ok ? "ok" : "error");
    }

    /** 自动备份设置（enabled / scope / retention）。 */
    @GetMapping("/settings")
    public Map<String, Object> settings() {
        return backupService.settings();
    }

    @PutMapping("/settings")
    public Map<String, Object> saveSettings(@RequestBody Map<String, Object> body) {
        backupService.saveSettings(body);
        return Map.of("status", "ok");
    }

    private static String ip(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}

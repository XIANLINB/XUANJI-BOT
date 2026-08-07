package dev.xuanji.console.service;

import com.sun.management.OperatingSystemMXBean;
import org.springframework.stereotype.Service;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统资源检测 — CPU / 物理内存 / 磁盘 / JVM 堆 / 运行时间 / OS 信息。
 *
 * <p>全部使用 JDK 内置 API（{@link OperatingSystemMXBean} + {@link Runtime} + {@link File}），
 * 无第三方依赖，Windows / Linux / macOS 通用。供运行监控页与模板推荐使用。
 */
@Service
public class SystemInfoService {

    private static final OperatingSystemMXBean OS =
            (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

    /** 采集一次系统资源快照（CPU 使用率为瞬时采样，前端可 30s 轮询）。 */
    public Map<String, Object> snapshot() {
        Map<String, Object> m = new LinkedHashMap<>();

        // ── CPU ──
        int cores = Runtime.getRuntime().availableProcessors();
        double cpuLoad = OS.getCpuLoad();            // 0.0~1.0，瞬时
        double sysLoadAvg = OS.getSystemLoadAverage(); // 1/5/15 分钟负载，Windows 上为 -1
        m.put("cpuCores", cores);
        m.put("cpuLoad", cpuLoad < 0 ? 0 : Math.round(cpuLoad * 1000) / 10.0);   // 百分比，1 位小数
        m.put("systemLoadAvg", sysLoadAvg);

        // ── 物理内存 ──
        long memTotal = OS.getTotalPhysicalMemorySize();
        long memFree = OS.getFreePhysicalMemorySize();
        long memUsed = memTotal - memFree;
        m.put("memTotal", memTotal);
        m.put("memFree", memFree);
        m.put("memUsed", memUsed);
        m.put("memRatio", memTotal > 0 ? Math.round(memUsed * 1000.0 / memTotal) / 10.0 : 0);

        // ── JVM 堆 ──
        Runtime rt = Runtime.getRuntime();
        long jvmMax = rt.maxMemory();
        long jvmTotal = rt.totalMemory();
        long jvmFree = rt.freeMemory();
        long jvmUsed = jvmTotal - jvmFree;
        m.put("jvmMax", jvmMax);
        m.put("jvmTotal", jvmTotal);
        m.put("jvmFree", jvmFree);
        m.put("jvmUsed", jvmUsed);
        m.put("jvmRatio", jvmMax > 0 ? Math.round(jvmUsed * 1000.0 / jvmMax) / 10.0 : 0);

        // ── 磁盘（全部根盘聚合）──
        long diskTotal = 0, diskFree = 0;
        List<Map<String, Object>> disks = new ArrayList<>();
        for (File root : File.listRoots()) {
            long t = root.getTotalSpace();
            long u = root.getUsableSpace();
            if (t <= 0) continue;
            diskTotal += t;
            diskFree += u;
            disks.add(Map.of(
                    "path", root.getPath(),
                    "total", t,
                    "free", u,
                    "used", t - u,
                    "ratio", Math.round((t - u) * 1000.0 / t) / 10.0));
        }
        m.put("diskTotal", diskTotal);
        m.put("diskFree", diskFree);
        m.put("diskUsed", diskTotal - diskFree);
        m.put("diskRatio", diskTotal > 0 ? Math.round((diskTotal - diskFree) * 1000.0 / diskTotal) / 10.0 : 0);
        m.put("disks", disks);

        // ── 运行时间 / 系统信息 ──
        m.put("uptimeSeconds", ManagementFactory.getRuntimeMXBean().getUptime() / 1000L);
        m.put("osName", System.getProperty("os.name"));
        m.put("osVersion", System.getProperty("os.version"));
        m.put("osArch", System.getProperty("os.arch"));
        m.put("javaVersion", System.getProperty("java.version"));
        m.put("javaVendor", System.getProperty("java.vendor"));
        m.put("processors", cores);
        return m;
    }
}

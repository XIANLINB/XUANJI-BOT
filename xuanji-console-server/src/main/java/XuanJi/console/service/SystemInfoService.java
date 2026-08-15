package XuanJi.console.service;

import com.sun.management.OperatingSystemMXBean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 系统资源检测 — CPU / 内存 / 磁盘 / JVM / 网络带宽 / GC / 进程线程数。
 *
 * <p>全部使用 JDK 内置 API + 部分平台特定文件（/proc/cpuinfo / PROCESSOR_IDENTIFIER），
 * 无第三方依赖。Windows / Linux / macOS 通用。
 *
 * <p>网络带宽 / 磁盘 IO 是<b>采样差值</b>：首次调用返回 0，第二次开始给出 MB/s 估算。
 */
@Slf4j
@Service
public class SystemInfoService {

    private static final OperatingSystemMXBean OS =
            (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

    // ───── 采样差值状态（跨 snapshot 调用累计） ─────
    private final AtomicLong lastSampleMs = new AtomicLong();
    /** key = interfaceName, value = total rx bytes 累计（snapshot 时刻） */
    private final Map<String, Long> lastRxBytes = new ConcurrentHashMap<>();
    /** key = interfaceName, value = total tx bytes 累计 */
    private final Map<String, Long> lastTxBytes = new ConcurrentHashMap<>();

    /** 采集一次系统资源快照（CPU 使用率为瞬时采样，前端可 5~30s 轮询）。 */
    public Map<String, Object> snapshot() {
        Map<String, Object> m = new LinkedHashMap<>();

        // ── CPU ──
        int cores = Runtime.getRuntime().availableProcessors();
        double cpuLoad = OS.getCpuLoad();
        double sysLoadAvg = OS.getSystemLoadAverage();
        m.put("cpuCores", cores);
        m.put("cpuLoad", cpuLoad < 0 ? 0 : Math.round(cpuLoad * 1000) / 10.0);
        m.put("systemLoadAvg", sysLoadAvg);
        m.put("cpuModel", detectCpuModel());

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

        // ── 磁盘（容量 + IO 速率采样） ──
        List<Map<String, Object>> disks = snapshotDisks();
        m.put("disks", disks);
        long diskTotal = 0, diskUsed = 0;
        for (Map<String, Object> d : disks) {
            diskTotal += ((Number) d.get("total")).longValue();
            diskUsed += ((Number) d.get("used")).longValue();
        }
        m.put("diskTotal", diskTotal);
        m.put("diskUsed", diskUsed);
        m.put("diskRatio", diskTotal > 0 ? Math.round(diskUsed * 1000.0 / diskTotal) / 10.0 : 0);

        // ── 运行时间 / 系统信息 ──
        m.put("uptimeSeconds", ManagementFactory.getRuntimeMXBean().getUptime() / 1000L);
        m.put("osName", System.getProperty("os.name"));
        m.put("osVersion", System.getProperty("os.version"));
        m.put("osArch", System.getProperty("os.arch"));
        m.put("javaVersion", System.getProperty("java.version"));
        m.put("javaVendor", System.getProperty("java.vendor"));

        // ── 网络带宽（采样差值，MB/s） ──
        m.put("network", snapshotNetwork());

        // ── JVM GC 详情（年轻代 / 老年代 累计） ──
        m.put("gc", snapshotGc());

        // ── 进程/线程数 + 文件句柄数 ──
        m.put("process", snapshotProcess());

        return m;
    }

    // ===================================================================
    //  CPU 型号检测（跨平台）
    // ===================================================================

    /** 检测 CPU 型号/频率（Windows=PROCESSOR_IDENTIFIER / Linux=/proc/cpuinfo / macOS=sysctl）。 */
    private static String detectCpuModel() {
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            // Windows
            if (os.contains("win")) {
                String id = System.getenv("PROCESSOR_IDENTIFIER");
                if (id != null && !id.isBlank()) return id.trim();
            }
            // Linux
            if (os.contains("linux") || os.contains("nix") || os.contains("nux")) {
                String cpu = Files.readString(Paths.get("/proc/cpuinfo"));
                for (String line : cpu.split("\n")) {
                    if (line.startsWith("model name")) {
                        int idx = line.indexOf(':');
                        if (idx > 0) return line.substring(idx + 1).trim();
                    }
                }
            }
            // macOS
            if (os.contains("mac")) {
                Process p = new ProcessBuilder("sysctl", "-n", "machdep.cpu.brand_string").start();
                String s = new String(p.getInputStream().readAllBytes()).trim();
                if (!s.isBlank()) return s;
            }
        } catch (Exception ignored) {
            // 取不到型号时静默降级
        }
        return "未知型号（" + Runtime.getRuntime().availableProcessors() + " 核）";
    }

    // ===================================================================
    //  网络带宽（采样差值，MB/s）
    // ===================================================================

    /**
     * 网络带宽采样：每次调用记录总 RX/TX bytes，下次调用算差值 → MB/s。
     * 跨平台：Windows 用 IpHlpAPI（暂不实现，简化为返回 0）；Linux 用 /proc/net/dev；
     * macOS 用 netstat -ib（暂不实现）。
     */
    private Map<String, Object> snapshotNetwork() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("interfaces", List.of());
        out.put("totalRxMBps", 0.0);
        out.put("totalTxMBps", 0.0);
        out.put("note", "未采集（首次调用 / 平台不支持）");

        long now = System.currentTimeMillis();
        Long lastMs = lastSampleMs.get();
        boolean firstSample = (lastMs == null);

        String os = System.getProperty("os.name", "").toLowerCase();
        if (!(os.contains("linux") || os.contains("nix") || os.contains("nux"))) {
            lastSampleMs.set(now);
            return out; // 非 Linux：暂不支持
        }

        Map<String, long[]> ifaces = new LinkedHashMap<>(); // name -> [rx, tx]
        try {
            List<String> lines = Files.readAllLines(Paths.get("/proc/net/dev"));
            for (String line : lines) {
                if (!line.contains(":")) continue;
                int colon = line.indexOf(':');
                String name = line.substring(0, colon).trim();
                if (name.equals("lo")) continue; // 跳过 loopback
                String[] parts = line.substring(colon + 1).trim().split("\\s+");
                if (parts.length < 9) continue;
                long rx = Long.parseLong(parts[0]);
                long tx = Long.parseLong(parts[8]);
                ifaces.put(name, new long[]{rx, tx});
            }
        } catch (IOException e) {
            return out;
        }

        List<Map<String, Object>> ifaceList = new ArrayList<>();
        double totalRx = 0, totalTx = 0;
        for (Map.Entry<String, long[]> e : ifaces.entrySet()) {
            String name = e.getKey();
            long rx = e.getValue()[0], tx = e.getValue()[1];
            double rxMBps = 0, txMBps = 0;
            Long lastRx = lastRxBytes.get(name);
            Long lastTx = lastTxBytes.get(name);
            if (!firstSample && lastRx != null && lastTx != null && lastMs != null) {
                long dtMs = now - lastMs;
                if (dtMs > 0) {
                    rxMBps = (rx - lastRx) / 1024.0 / 1024.0 / (dtMs / 1000.0);
                    txMBps = (tx - lastTx) / 1024.0 / 1024.0 / (dtMs / 1000.0);
                    if (rxMBps < 0) rxMBps = 0;
                    if (txMBps < 0) txMBps = 0;
                }
            }
            lastRxBytes.put(name, rx);
            lastTxBytes.put(name, tx);
            totalRx += rxMBps;
            totalTx += txMBps;
            ifaceList.add(Map.of(
                    "name", name,
                    "rxBytes", rx,
                    "txBytes", tx,
                    "rxMBps", Math.round(rxMBps * 100) / 100.0,
                    "txMBps", Math.round(txMBps * 100) / 100.0));
        }
        lastSampleMs.set(now);

        out.put("interfaces", ifaceList);
        out.put("totalRxMBps", Math.round(totalRx * 100) / 100.0);
        out.put("totalTxMBps", Math.round(totalTx * 100) / 100.0);
        out.put("note", firstSample ? "首次采样，需再等 5s 才有速率" : "基于两次采样差值");
        return out;
    }

    // ===================================================================
    //  磁盘（容量 + IO 速率采样）
    // ===================================================================

    private List<Map<String, Object>> snapshotDisks() {
        long diskTotal = 0, diskFree = 0;
        List<Map<String, Object>> disks = new ArrayList<>();
        for (File root : File.listRoots()) {
            long t = root.getTotalSpace();
            long u = root.getUsableSpace();
            if (t <= 0) continue;
            diskTotal += t;
            diskFree += u;
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("path", root.getPath());
            info.put("total", t);
            info.put("free", u);
            info.put("used", t - u);
            info.put("ratio", Math.round((t - u) * 1000.0 / t) / 10.0);
            // 文件系统类型（Windows: FileStore；Linux: Files）
            String fsType = "unknown";
            try {
                FileStore store = Files.getFileStore(Paths.get(root.getPath()));
                info.put("fsType", store.type());
            } catch (Exception e) {
                info.put("fsType", fsType);
            }
            disks.add(info);
        }
        return disks;
    }

    // ===================================================================
    //  JVM GC（年轻代 / 老年代 各收集器累计）
    // ===================================================================

    private Map<String, Object> snapshotGc() {
        Map<String, Object> out = new LinkedHashMap<>();
        long totalCount = 0;
        long totalTimeMs = 0;
        List<Map<String, Object>> collectors = new ArrayList<>();
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            long count = gc.getCollectionCount();
            long time = gc.getCollectionTime();
            totalCount += count;
            totalTimeMs += time;
            collectors.add(Map.of(
                    "name", gc.getName(),
                    "count", count,
                    "timeMs", time));
        }
        out.put("collectors", collectors);
        out.put("totalCount", totalCount);
        out.put("totalTimeMs", totalTimeMs);
        out.put("avgPerMin", totalTimeMs > 0 && totalCount > 0
                ? Math.round(totalTimeMs * 60.0 / Math.max(1, ManagementFactory.getRuntimeMXBean().getUptime() / 1000.0) * 10) / 10.0
                : 0.0);
        return out;
    }

    // ===================================================================
    //  进程 / 线程数 / 文件句柄
    // ===================================================================

    private Map<String, Object> snapshotProcess() {
        Map<String, Object> out = new LinkedHashMap<>();
        ThreadMXBeanEx tmx = OSThreadCountSafe.get();
        out.put("threadCount", tmx.threadCount);
        // 进程数与文件句柄：跨平台差异大
        try {
            if (OS instanceof com.sun.management.UnixOperatingSystemMXBean uos) {
                out.put("openFiles", uos.getOpenFileDescriptorCount());
                out.put("maxFiles", uos.getMaxFileDescriptorCount());
            } else {
                out.put("openFiles", "N/A（仅 Unix-like 支持）");
                out.put("maxFiles", "N/A（仅 Unix-like 支持）");
            }
        } catch (Throwable t) {
            out.put("openFiles", "N/A");
            out.put("maxFiles", "N/A");
        }
        return out;
    }

    /** 跨 JDK 版本的线程数取法（ThreadMXBean.getAllThreadIds() 在某些 JDK 上行为不一致）。 */
    private static final class OSThreadCountSafe {
        static ThreadMXBeanEx get() {
            try {
                java.lang.management.ThreadMXBean t = ManagementFactory.getThreadMXBean();
                return new ThreadMXBeanEx(t.getThreadCount());
            } catch (Throwable e) {
                return new ThreadMXBeanEx(-1);
            }
        }
    }
    private record ThreadMXBeanEx(int threadCount) {}
}
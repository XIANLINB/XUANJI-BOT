package dev.xuanji.adapter.qq.websocket;

import dev.xuanji.api.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * WebSocket 连接监控与管理接口
 *
 * <p>提供 REST API 用于监控和管理所有机器人的 WebSocket 连接状态。
 * 可用于运维面板、健康检查端点或调试工具。
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>GET  /api/v1/websocket/metrics           — 全局指标（连接数、事件数等）</li>
 *   <li>GET  /api/v1/websocket/status             — 所有机器人的状态列表</li>
 *   <li>GET  /api/v1/websocket/status/{robotId}   — 单个机器人的状态</li>
 *   <li>POST /api/v1/websocket/restart/{robotId}  — 重启指定机器人的连接</li>
 *   <li>POST /api/v1/websocket/stop/{robotId}     — 停止指定机器人的连接</li>
 *   <li>POST /api/v1/websocket/start/{robotId}    — 启动指定机器人的连接</li>
 * </ul>
 *
 * <p>所有接口返回统一的 {@link R} 响应格式。
 *
 * @see QqBotWsManager 底层连接管理器
 */
@RestController
@RequestMapping("/api/v1/websocket")
@RequiredArgsConstructor
public class WebSocketController {

    /** WebSocket 连接管理器，处理所有连接生命周期操作 */
    private final QqBotWsManager wsManager;

    /**
     * 获取全局 WebSocket 指标
     *
     * @return 包含 totalClients、connected、reconnecting、disconnected、totalEvents、totalReconnects
     */
    @GetMapping("/metrics")
    public R<Map<String, Object>> getMetrics() {
        return R.ok(wsManager.getMetrics());
    }

    /**
     * 获取所有机器人的状态列表
     *
     * @return 每个元素包含 key、robotId、envType、state、running、totalEvents、totalReconnects
     */
    @GetMapping("/status")
    public R<List<Map<String, Object>>> getAllStatus() {
        return R.ok(wsManager.getAllStatus());
    }

    /**
     * 获取指定机器人的状态
     *
     * @param robotId 机器人 ID（路径参数）
     * @param envType 环境类型（查询参数，默认 SANDBOX）
     * @return 包含 running、state、totalEvents、totalReconnects 的状态信息
     */
    @GetMapping("/status/{robotId}")
    public R<Map<String, Object>> getStatus(@PathVariable String robotId,
                                             @RequestParam(defaultValue = "SANDBOX") String envType) {
        return R.ok(wsManager.getStatus(robotId, envType));
    }

    /**
     * 重启指定机器人的 WebSocket 连接
     *
     * <p>先停止再启动，用于配置变更或连接异常时的手动恢复。
     *
     * @param robotId 机器人 ID
     * @param envType 环境类型（默认 SANDBOX）
     * @return 操作结果
     */
    @PostMapping("/restart/{robotId}")
    public R<Void> restart(@PathVariable String robotId,
                           @RequestParam(defaultValue = "SANDBOX") String envType) {
        wsManager.restart(robotId, envType);
        return R.ok();
    }

    /**
     * 停止指定机器人的所有 WebSocket 连接
     *
     * @param robotId 机器人 ID
     * @return 操作结果
     */
    @PostMapping("/stop/{robotId}")
    public R<Void> stop(@PathVariable String robotId) {
        wsManager.stop(robotId);
        return R.ok();
    }

    /**
     * 启动指定机器人的 WebSocket 连接
     *
     * <p>机器人必须已通过 registerRobot 注册，否则会抛出异常。
     *
     * @param robotId 机器人 ID
     * @param envType 环境类型（默认 SANDBOX）
     * @return 操作结果
     */
    @PostMapping("/start/{robotId}")
    public R<Void> start(@PathVariable String robotId,
                         @RequestParam(defaultValue = "SANDBOX") String envType) {
        wsManager.start(robotId, envType);
        return R.ok();
    }
}

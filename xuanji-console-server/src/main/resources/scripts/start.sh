#!/bin/bash
# =============================================================================
# 璇玑机器人框架 · 启动守护脚本
# =============================================================================
# 用法: ./start.sh {start|stop|restart|status}
#
# 功能:
#   - start:    启动框架（如已运行则提示并退出）
#   - stop:     优雅停止（30 秒超时强杀）
#   - restart:  stop + start（用于前端「重启框架」按钮）
#   - status:   查看运行状态
#
# 部署:
#   1. 把这个脚本放在 JAR 包同目录（cp /path/to/start.sh ./start.sh）
#   2. chmod +x start.sh  (Linux/macOS)
#   3. ./start.sh start    启动
#   4. 前端「一键重启」会异步调用 Spring 退出 → 本脚本检测进程退出 → 自动拉起新进程
# =============================================================================

set -e

JAR_FILE="${JAR_FILE:-xuanji-starter-1.0.0-SNAPSHOT.jar}"
PID_FILE="${PID_FILE:-xuanji.pid}"
LOG_FILE="${LOG_FILE:-logs/xuanji-bot.log}"
WAIT_SECONDS="${WAIT_SECONDS:-30}"

# --- 颜色（终端好看） ---
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }

is_running() {
    if [ -f "$PID_FILE" ]; then
        local pid
        pid=$(cat "$PID_FILE" 2>/dev/null || echo "")
        if [ -n "$pid" ] && ps -p "$pid" > /dev/null 2>&1; then
            echo "$pid"
            return 0
        fi
    fi
    return 1
}

start() {
    if running_pid=$(is_running); then
        log_warn "框架已在运行 PID=$running_pid，无需重复启动"
        return 0
    fi
    if [ ! -f "$JAR_FILE" ]; then
        log_error "未找到 JAR 包: $JAR_FILE（请把 start.sh 放在 JAR 同目录）"
        exit 1
    fi
    mkdir -p "$(dirname "$LOG_FILE")"
    log_info "启动框架: java -jar $JAR_FILE"
    nohup java -jar "$JAR_FILE" > "$LOG_FILE" 2>&1 &
    local new_pid=$!
    echo "$new_pid" > "$PID_FILE"
    log_info "已启动 PID=$new_pid，日志: $LOG_FILE"
    log_info "等待 5 秒确认进程稳定..."
    sleep 5
    if ps -p "$new_pid" > /dev/null 2>&1; then
        log_info "✓ 框架运行中（PID=$new_pid）"
    else
        log_error "✗ 启动失败，进程 5 秒内退出，请查看日志: tail -50 $LOG_FILE"
        rm -f "$PID_FILE"
        exit 1
    fi
}

stop() {
    local pid
    if ! pid=$(is_running); then
        log_warn "框架未运行"
        rm -f "$PID_FILE"
        return 0
    fi
    log_info "优雅停止 PID=$pid（kill 信号）"
    kill "$pid" 2>/dev/null || true
    # 等待最长 30 秒
    for i in $(seq 1 "$WAIT_SECONDS"); do
        if ! ps -p "$pid" > /dev/null 2>&1; then
            log_info "✓ 已停止（耗时 ${i}s）"
            rm -f "$PID_FILE"
            return 0
        fi
        sleep 1
    done
    log_warn "$WAIT_SECONDS 秒内未退出，强杀 PID=$pid"
    kill -9 "$pid" 2>/dev/null || true
    sleep 2
    rm -f "$PID_FILE"
    log_info "✓ 已强杀"
}

status() {
    if pid=$(is_running); then
        log_info "运行中 PID=$pid"
        if [ -f "$LOG_FILE" ]; then
            log_info "日志尾部:"
            tail -5 "$LOG_FILE" 2>/dev/null | sed 's/^/    /'
        fi
    else
        log_warn "未运行"
        exit 1
    fi
}

case "${1:-}" in
    start)    start ;;
    stop)     stop ;;
    restart)  stop || true; start ;;
    status)   status ;;
    *)
        echo "用法: $0 {start|stop|restart|status}"
        echo ""
        echo "环境变量（可选覆盖）:"
        echo "  JAR_FILE       JAR 包路径（默认: $JAR_FILE）"
        echo "  PID_FILE       PID 文件路径（默认: $PID_FILE）"
        echo "  LOG_FILE       日志文件路径（默认: $LOG_FILE）"
        echo "  WAIT_SECONDS   stop 等待秒数（默认: $WAIT_SECONDS）"
        exit 2
        ;;
esac
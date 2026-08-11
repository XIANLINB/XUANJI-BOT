package XuanJi.core.action;

import XuanJi.api.action.PlatformActionHandler;
import XuanJi.api.action.PlatformActionHub;
import XuanJi.api.action.PlatformActionProvider;
import XuanJi.api.action.OperationAuditSink;
import XuanJi.api.adapter.BotContextBinder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * 平台统一动作分发中枢实现 — 按平台注册动作表，按 botKey 路由分发。
 *
 * <p>分发顺序：botKey 带 {@code 前缀:} 精确匹配 → {@link PlatformActionProvider#matches} 兜底
 * → 空串/未识别回退第一个注册平台。分发前若提供者本身是 {@link BotContextBinder}
 * （如 qqbot），先绑定 botKey 上下文再执行，使处理器内能解析到正确 token/环境。
 */
@Component
public class PlatformActionHubImpl implements PlatformActionHub {

    private final Map<String, PlatformActionProvider> providers = new LinkedHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ObjectProvider<OperationAuditSink> auditSinkProvider;

    public PlatformActionHubImpl(ObjectProvider<OperationAuditSink> auditSinkProvider) {
        this.auditSinkProvider = auditSinkProvider;
    }

    @Override
    public void register(PlatformActionProvider provider) {
        if (provider == null || provider.platform() == null || provider.platform().isBlank()) {
            return;
        }
        lock.writeLock().lock();
        try {
            providers.putIfAbsent(provider.platform(), provider);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Map<String, Object> dispatch(String botKey, String action, Map<String, Object> params) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", false);
        PlatformActionProvider provider = resolveProvider(botKey);
        if (provider == null) {
            out.put("error", "未注册任何平台适配器");
            return out;
        }
        PlatformActionHandler handler = provider.actions().get(action);
        if (handler == null) {
            out.put("error", "平台 " + provider.platform() + " 不支持动作 " + action);
            return out;
        }
        Map<String, Object> p = new LinkedHashMap<>();
        if (params != null) p.putAll(params);
        p.put("_botKey", botKey == null ? "" : botKey);
        try {
            Object result = runWith(provider, botKey, () -> handler.handle(p));
            if (result instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked")
                Map<String, Object> rm = (Map<String, Object>) m;
                if (rm.containsKey("ok") && (rm.containsKey("data") || rm.containsKey("error"))) {
                    // 结果自带 ok/data|error 契约（如 onebot receiptMap）→ 直接采纳
                    out.putAll(rm);
                    out.putIfAbsent("ok", true);
                } else {
                    // 裸 Map（如 qqbot 的群信息原始报文）→ 统一包进 data
                    out.put("ok", true);
                    out.put("data", rm);
                }
            } else {
                out.put("ok", true);
                out.put("data", result);
            }
            audit(action, p, out);
            return out;
        } catch (Exception e) {
            out.put("ok", false);
            out.put("error", e.getMessage() == null ? e.toString() : e.getMessage());
            audit(action, p, out);
            return out;
        }
    }

    /** 管理操作审计回调：存在 OperationAuditSink 实现时记录（成功/失败/被拒全记，异常不阻断）。 */
    private void audit(String action, Map<String, Object> params, Map<String, Object> result) {
        try {
            OperationAuditSink sink = auditSinkProvider.getIfAvailable();
            if (sink != null) {
                String botKey = params == null ? null : String.valueOf(params.get("_botKey"));
                sink.record(botKey, action, params, result);
            }
        } catch (Exception ignored) { /* 审计失败不影响主流程 */ }
    }

    @Override
    public List<String> listActions(String botKey) {
        PlatformActionProvider provider = resolveProvider(botKey);
        if (provider == null) {
            return List.of();
        }
        return new ArrayList<>(provider.actions().keySet());
    }

    /** 提供者若实现 BotContextBinder，先绑定 botKey 上下文再执行（便于处理器内解析 token）。 */
    private Object runWith(PlatformActionProvider provider, String botKey, Supplier<Object> task) {
        if (provider instanceof BotContextBinder binder) {
            Object[] box = new Object[1];
            binder.runWith(botKey, () -> box[0] = task.get());
            return box[0];
        }
        return task.get();
    }

    private PlatformActionProvider resolveProvider(String botKey) {
        lock.readLock().lock();
        try {
            if (botKey != null && !botKey.isBlank()) {
                int i = botKey.indexOf(':');
                if (i > 0) {
                    PlatformActionProvider byPrefix = providers.get(botKey.substring(0, i));
                    if (byPrefix != null) {
                        return byPrefix;
                    }
                }
                for (PlatformActionProvider p : providers.values()) {
                    if (p.matches(botKey)) {
                        return p;
                    }
                }
            }
            return providers.values().stream().findFirst().orElse(null);
        } finally {
            lock.readLock().unlock();
        }
    }
}

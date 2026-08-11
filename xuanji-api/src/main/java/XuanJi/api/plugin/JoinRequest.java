package XuanJi.api.plugin;

import java.util.Map;

/**
 * 入群申请信息（类型化封装，框架解析字段后下发给插件，插件自行决策审批）。
 *
 * <p>同时兼容两个数据源：
 * <ul>
 *   <li><b>入群申请列表</b>（{@link PluginServices#listGroupJoinRequests}）：平台返回 snake_case
 *       （member_openid / join_request_id / apply_at / apply_source / verify_info / _verify_parsed）</li>
 *   <li><b>申请加群事件</b>（SDK {@code GroupMessageEvent#getJoinRequestInfo()}）：框架注入 camelCase
 *       （memberOpenid / joinRequestId / applyAt / applySource / verifyInfo / verifyParsed）</li>
 * </ul>
 *
 * <p>{@code verifyParsed} 是框架解析后的统一验证信息结构：
 * {@code method / verifyMessage / question / answer / qaMode}（原始 {@code verifyInfo} 保留不动）。
 */
public record JoinRequest(
        String memberOpenid,
        String username,
        String applyAt,
        String applySource,
        String joinRequestId,
        boolean bot,
        Map<String, Object> verifyInfo,
        Map<String, Object> verifyParsed) {

    /** 从平台原始 item / 事件信息 Map 构造（兼容 snake_case 与 camelCase 两种 key 风格）。 */
    public static JoinRequest from(Map<?, ?> m) {
        if (m == null) return null;
        String memberOpenid = str(m, "memberOpenid", "member_openid");
        String username = str(m, "username");
        String applyAt = str(m, "applyAt", "apply_at");
        String applySource = str(m, "applySource", "apply_source");
        String joinRequestId = str(m, "joinRequestId", "join_request_id");
        boolean bot = Boolean.TRUE.equals(m.get("bot"));
        Map<String, Object> verifyInfo = map(m, "verifyInfo", "verify_info");
        Map<String, Object> verifyParsed = map(m, "verifyParsed", "_verify_parsed");
        // verifyParsed 缺失时从 verifyInfo 兜底提取基础字段（method / verifyMessage），保证常用字段可用
        if (verifyParsed == null && verifyInfo != null) {
            String method = String.valueOf(verifyInfo.getOrDefault("method", ""));
            String verifyMessage = verifyInfo.get("verify_message") == null
                    ? "" : String.valueOf(verifyInfo.get("verify_message"));
            verifyParsed = Map.of("method", method, "verifyMessage", verifyMessage);
        }
        return new JoinRequest(memberOpenid, username, applyAt, applySource,
                joinRequestId, bot, verifyInfo, verifyParsed);
    }

    /** 是否设置了入群问题（有问答校验）。 */
    public boolean isQaMode() {
        return verifyParsed != null && Boolean.TRUE.equals(verifyParsed.get("qaMode"));
    }

    /** 入群问题（isQaMode 为 true 时有效）。 */
    public String getQuestion() {
        Object v = verifyParsed == null ? null : verifyParsed.get("question");
        return v == null ? "" : String.valueOf(v);
    }

    /** 申请者填写的答案（无入群问题时即验证消息内容）。 */
    public String getAnswer() {
        Object v = verifyParsed == null ? null : verifyParsed.get("answer");
        return v == null ? "" : String.valueOf(v);
    }

    /** 验证消息（verify_message / verifyMessage；无入群问题时即用户填写内容）。 */
    public String getVerifyMessage() {
        Object v = verifyParsed == null ? null : verifyParsed.get("verifyMessage");
        if (v == null) return getAnswer();
        return String.valueOf(v);
    }

    /** 验证方式：verify_message / admin_review_qa 等。 */
    public String getMethod() {
        Object v = verifyParsed == null ? null : verifyParsed.get("method");
        return v == null ? "" : String.valueOf(v);
    }

    private static String str(Map<?, ?> m, String... keys) {
        for (String k : keys) {
            Object v = m.get(k);
            if (v != null && !String.valueOf(v).isBlank()) return String.valueOf(v);
        }
        return "";
    }

    private static Map<String, Object> map(Map<?, ?> m, String... keys) {
        for (String k : keys) {
            if (m.get(k) instanceof Map<?, ?> mm) {
                @SuppressWarnings("unchecked")
                Map<String, Object> out = (Map<String, Object>) mm;
                return out;
            }
        }
        return null;
    }
}

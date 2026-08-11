package XuanJi.api.plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 入群申请列表（类型化封装）。
 *
 * <p>兼容两种响应包装：{@code {data:{list:[...], next_cursor}}（框架 actionData 返回）} 与
 * {@code {list:[...]}}（直接透传）。插件直接遍历 {@link #requests()} 即可，无需手工拆 Map。
 */
public record JoinRequestList(List<JoinRequest> requests, String nextCursor) {

    /** 从响应 Map 构造（兼容 {data:{list}} 与 {list}）。 */
    public static JoinRequestList from(Map<?, ?> resp) {
        if (resp == null) return new JoinRequestList(List.of(), "");
        Map<?, ?> data = resp.get("data") instanceof Map<?, ?> dm ? dm : resp;
        String nextCursor = data.get("next_cursor") == null ? "" : String.valueOf(data.get("next_cursor"));
        List<JoinRequest> out = new ArrayList<>();
        Object listObj = data.get("list");
        if (listObj instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    JoinRequest req = JoinRequest.from(m);
                    if (req != null) out.add(req);
                }
            }
        }
        return new JoinRequestList(List.copyOf(out), nextCursor);
    }

    public boolean isEmpty() {
        return requests == null || requests.isEmpty();
    }

    public int size() {
        return requests == null ? 0 : requests.size();
    }
}

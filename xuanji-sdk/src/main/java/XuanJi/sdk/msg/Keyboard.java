package XuanJi.sdk.msg;

import java.util.ArrayList;
import java.util.List;

/**
 * 按钮键盘构建器 — 支持最多 5 行 × 5 列。
 *
 * <pre>
 * String kb = Keyboard.create()
 *     .row()
 *         .btn("sign_in", "签到", "签到")
 *         .btn("bank", "银行", "银行")
 *     .endRow()
 *     .row()
 *         .btn("help", "帮助", "帮助")
 *     .endRow()
 *     .build();
 * </pre>
 */
public class Keyboard {

    /** 全局最多 5 行 */
    private static final int MAX_ROWS = 5;
    /** 每行最多 5 个按钮 */
    private static final int MAX_COLS = 5;

    private final List<List<Button>> rows = new ArrayList<>();
    private List<Button> currentRow = new ArrayList<>();

    private Keyboard() {}

    public static Keyboard create() { return new Keyboard(); }

    /** 开始新的一行 */
    public Keyboard row() {
        if (!currentRow.isEmpty()) {
            rows.add(currentRow);
            currentRow = new ArrayList<>();
        }
        return this;
    }

    /** 结束当前行 */
    public Keyboard endRow() {
        if (!currentRow.isEmpty()) {
            rows.add(currentRow);
            currentRow = new ArrayList<>();
        }
        return this;
    }

    /**
     * 在当前行添加按钮。
     * @param id    回调标识
     * @param label 按钮文字
     * @param data  携带数据
     */
    public Keyboard btn(String id, String label, String data) {
        if (currentRow.size() >= MAX_COLS) {
            rows.add(currentRow);
            currentRow = new ArrayList<>();
        }
        if (rows.size() + (currentRow.isEmpty() ? 0 : 1) > MAX_ROWS) {
            return this; // 超过 5 行，忽略
        }
        currentRow.add(new Button(id, label, data));
        return this;
    }

    /** 构建 QQ 键盘 JSON */
    public String build() {
        endRow(); // 确保最后一行加入

        StringBuilder sb = new StringBuilder("{\"content\":{\"rows\":[");
        boolean firstRow = true;
        for (List<Button> row : rows) {
            if (!firstRow) sb.append(",");
            sb.append("{\"buttons\":[");
            boolean firstBtn = true;
            for (Button btn : row) {
                if (!firstBtn) sb.append(",");
                sb.append("{\"id\":\"").append(esc(btn.id()))
                        .append("\",\"render_data\":{\"label\":\"").append(esc(btn.label()))
                        .append("\",\"visited_label\":\"").append(esc(btn.label()))
                        .append("\"},\"action\":{\"type\":0,\"permission\":{\"type\":2}")
                        .append(",\"data\":\"").append(esc(btn.data())).append("\"}}");
                firstBtn = false;
            }
            sb.append("]}");
            firstRow = false;
        }
        sb.append("]}}");
        return sb.toString();
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record Button(String id, String label, String data) {}
}

package XuanJi.sdk.msg;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ark 消息构建器 — 支持 Ark23（列表）、Ark24（图文）、Ark37（大图）。
 *
 * <pre>
 * // Ark24
 * String json = Ark24.create()
 *     .title("标题").desc("描述").prompt("提示")
 *     .img("https://...").link("https://...").subtitle("子标题")
 *     .build();
 * bot.replyArk("24", json);
 * </pre>
 */
public final class Ark {

    /** Ark24 大图卡片 */
    public static class Ark24 {
        private String title, desc, prompt, img, link, subtitle, metaDesc;

        public static Ark24 create() { return new Ark24(); }
        public Ark24 title(String v)   { this.title = v; return this; }
        public Ark24 desc(String v)    { this.desc = v; return this; }
        public Ark24 prompt(String v)  { this.prompt = v; return this; }
        public Ark24 img(String v)     { this.img = v; return this; }
        public Ark24 link(String v)    { this.link = v; return this; }
        public Ark24 subtitle(String v){ this.subtitle = v; return this; }
        public Ark24 metaDesc(String v){ this.metaDesc = v; return this; }

        public String build() {
            List<Map<String, String>> kvs = new ArrayList<>();
            if (desc != null)     kvs.add(kv("#DESC#", desc));
            if (prompt != null)   kvs.add(kv("#PROMPT#", prompt));
            if (title != null)    kvs.add(kv("#TITLE#", title));
            if (metaDesc != null) kvs.add(kv("#METADESC#", metaDesc));
            if (img != null)      kvs.add(kv("#IMG#", img));
            if (link != null)     kvs.add(kv("#LINK#", link));
            if (subtitle != null) kvs.add(kv("#SUBTITLE#", subtitle));
            return toArkJson(24, kvs);
        }
    }

    /** Ark23 列表卡片 */
    public static class Ark23 {
        private String desc, prompt, title, link, metaTitle, metaDesc, metaIcon;
        private final List<String> items = new ArrayList<>();

        public static Ark23 create() { return new Ark23(); }
        public Ark23 title(String v)     { this.title = v; return this; }
        public Ark23 desc(String v)      { this.desc = v; return this; }
        public Ark23 prompt(String v)    { this.prompt = v; return this; }
        public Ark23 link(String v)      { this.link = v; return this; }
        public Ark23 metaTitle(String v) { this.metaTitle = v; return this; }
        public Ark23 metaDesc(String v)  { this.metaDesc = v; return this; }
        public Ark23 metaIcon(String v)  { this.metaIcon = v; return this; }
        /** 添加列表项（可含 link） */
        public Ark23 item(String text)            { items.add(text); return this; }
        public Ark23 item(String text, String lk) { items.add(text + "||" + lk); return this; }

        public String build() {
            StringBuilder list = new StringBuilder();
            for (int i = 0; i < items.size(); i++) {
                if (i > 0) list.append("|");
                String[] parts = items.get(i).split("\\|\\|", 2);
                list.append(parts[0]);
            }
            List<Map<String, String>> kvs = new ArrayList<>();
            if (list.length() > 0) kvs.add(kv("#LIST#", list.toString()));
            if (desc != null)      kvs.add(kv("#DESC#", desc));
            if (prompt != null)    kvs.add(kv("#PROMPT#", prompt));
            if (title != null)     kvs.add(kv("#TITLE#", title));
            if (link != null)      kvs.add(kv("#LINK#", link));
            if (metaTitle != null) kvs.add(kv("#METATITLE#", metaTitle));
            if (metaDesc != null)  kvs.add(kv("#METADESC#", metaDesc));
            if (metaIcon != null)  kvs.add(kv("#METAICON#", metaIcon));
            return toArkJson(23, kvs);
        }
    }

    /** Ark37 大图通知卡片 */
    public static class Ark37 {
        private String prompt, metaTitle, metaSubtitle, metaCover, metaUrl;

        public static Ark37 create() { return new Ark37(); }
        public Ark37 prompt(String v)       { this.prompt = v; return this; }
        public Ark37 metaTitle(String v)    { this.metaTitle = v; return this; }
        public Ark37 metaSubtitle(String v) { this.metaSubtitle = v; return this; }
        public Ark37 metaCover(String v)    { this.metaCover = v; return this; }
        public Ark37 metaUrl(String v)      { this.metaUrl = v; return this; }

        public String build() {
            List<Map<String, String>> kvs = new ArrayList<>();
            if (prompt != null)       kvs.add(kv("#PROMPT#", prompt));
            if (metaTitle != null)    kvs.add(kv("#METATITLE#", metaTitle));
            if (metaSubtitle != null) kvs.add(kv("#METASUBTITLE#", metaSubtitle));
            if (metaCover != null)    kvs.add(kv("#METACOVER#", metaCover));
            if (metaUrl != null)      kvs.add(kv("#METAURL#", metaUrl));
            return toArkJson(37, kvs);
        }
    }

    private static String toArkJson(int templateId, List<Map<String, String>> kvs) {
        StringBuilder sb = new StringBuilder("{\"ark\":{\"template_id\":").append(templateId).append(",\"kv\":[");
        for (int i = 0; i < kvs.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"key\":\"").append(esc(kvs.get(i).get("k")))
              .append("\",\"value\":\"").append(esc(kvs.get(i).get("v"))).append("\"}");
        }
        sb.append("]}}");
        return sb.toString();
    }

    private static Map<String, String> kv(String key, String value) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("k", key); m.put("v", value); return m;
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

package dev.xuanji.adapter.onebot.session;

public interface OneBotSession {
    public static final String PLACEHOLDER_FORWARD = "forward";
    public static final String PLACEHOLDER_UNKNOWN = "unknown";

    public static boolean isPlaceholderId(String selfId) {
        return selfId == null || selfId.isBlank() || PLACEHOLDER_FORWARD.equals(selfId) || PLACEHOLDER_UNKNOWN.equals(selfId);
    }

    public String selfId();

    default public boolean rebindSelfId(String realSelfId) {
        return false;
    }

    public String direction();

    public boolean isOpen();

    public void sendText(String var1);

    public void close();
}


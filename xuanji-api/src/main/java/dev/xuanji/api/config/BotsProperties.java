package dev.xuanji.api.config;

import java.util.Map;

/**
 * 框架全局配置 — 对应 application.yml 的 {@code xuanji.*} 段。
 */
public class BotsProperties {

    /** 全局 Webhook 回调域名（webhook 模式时必填） */
    private String webhookUrl;

    /** 是否使用新开放平台（QQ 适配器专用） */
    private boolean newOpenBot;

    /** 是否忽略机器人自身消息 */
    private boolean ignoreBotMessages = false;

    /** Bot 实例列表，key = botKey（别名） */
    private Map<String, BotInstanceProperties> bots;

    // getter/setter

    public String getWebhookUrl() { return webhookUrl; }
    public void setWebhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; }
    public boolean isNewOpenBot() { return newOpenBot; }
    public void setNewOpenBot(boolean newOpenBot) { this.newOpenBot = newOpenBot; }
    public boolean isIgnoreBotMessages() { return ignoreBotMessages; }
    public void setIgnoreBotMessages(boolean ignore) { this.ignoreBotMessages = ignore; }
    public Map<String, BotInstanceProperties> getBots() { return bots; }
    public void setBots(Map<String, BotInstanceProperties> bots) { this.bots = bots; }

    // ==================== 单实例配置 ====================

    public static class BotInstanceProperties {
        private String adapter;          // qq-official / onebot / feishu
        private String appId;
        private String secret;
        private boolean sandbox;
        private String connectionMethod; // websocket / webhook / polling

        public String getAdapter() { return adapter; }
        public void setAdapter(String adapter) { this.adapter = adapter; }
        public String getAppId() { return appId; }
        public void setAppId(String appId) { this.appId = appId; }
        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
        public boolean isSandbox() { return sandbox; }
        public void setSandbox(boolean sandbox) { this.sandbox = sandbox; }
        public String getConnectionMethod() { return connectionMethod; }
        public void setConnectionMethod(String connectionMethod) { this.connectionMethod = connectionMethod; }
    }
}

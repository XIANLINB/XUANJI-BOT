/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  dev.xuanji.core.storage.FrameworkBotRepository
 *  dev.xuanji.core.storage.PlatformDataProvider
 *  lombok.Generated
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package dev.xuanji.adapter.onebot.storage;

import dev.xuanji.adapter.onebot.session.OneBotSession;
import dev.xuanji.adapter.onebot.session.OneBotSessionRegistry;
import dev.xuanji.adapter.onebot.storage.OneBotRepository;
import dev.xuanji.core.storage.FrameworkBotRepository;
import dev.xuanji.core.storage.PlatformDataProvider;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Generated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OneBotPlatformDataProvider
implements PlatformDataProvider {
    @Generated
    private static final Logger log = LoggerFactory.getLogger(OneBotPlatformDataProvider.class);
    private final OneBotRepository repo;
    private final OneBotSessionRegistry sessionRegistry;
    private final FrameworkBotRepository frameworkBotRepository;

    public String platform() {
        return "onebot";
    }

    public List<String> listInstanceIds() {
        return this.repo.listInstanceIds();
    }

    public Map<String, Object> getBotConfig(String instanceId) {
        Map<String, Object> row = this.repo.getBotRow(instanceId);
        if (row.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Object> cfg = new LinkedHashMap<String, Object>();
        cfg.put("appId", OneBotPlatformDataProvider.str(row, "BOT_APPID"));
        cfg.put("clientSecret", "");
        cfg.put("connectionMethod", "websocket");
        cfg.put("sandbox", false);
        cfg.put("status", OneBotPlatformDataProvider.str(row, "STATUS"));
        cfg.put("webhookUrl", "");
        return cfg;
    }

    public Map<String, Object> getBotInfo(String instanceId) {
        Map<String, Object> info = this.repo.getBotInfo(instanceId);
        return info.isEmpty() ? Map.of() : info;
    }

    public String getConnectionType(String instanceId) {
        return "websocket";
    }

    public List<Map<String, Object>> listGroups(String instanceId) {
        return this.repo.listGroups(instanceId);
    }

    public List<Map<String, Object>> listFriends(String instanceId) {
        return this.repo.listUsers(instanceId);
    }

    public List<Map<String, Object>> listGroupMembers(String instanceId, String groupId) {
        return this.repo.listGroupMembers(instanceId, groupId);
    }

    public List<Map<String, Object>> listMessages(String instanceId, String chatType, int limit) {
        return this.repo.listMessages(instanceId, chatType, limit);
    }

    public List<Map<String, Object>> listMessagesByTarget(String instanceId, String chatType, String targetId, int limit) {
        return this.repo.listMessagesByTarget(instanceId, chatType, targetId, limit);
    }

    public List<Map<String, Object>> listEvents(String instanceId, int limit) {
        return this.repo.listEvents(instanceId, limit);
    }

    public long countGroups(String instanceId) {
        return this.repo.countGroups(instanceId);
    }

    public long countFriends(String instanceId) {
        return this.repo.countUsers(instanceId);
    }

    public long countMessagesSince(String instanceId, String chatType, long sinceEpochSeconds) {
        return this.repo.countMessagesSince(instanceId, chatType, sinceEpochSeconds);
    }

    public long countEventsSince(String instanceId, String eventKind, long sinceEpochSeconds) {
        String onebotType = switch (eventKind) {
            case "GROUP_ADD" -> "notice/group_increase";
            case "GROUP_DEL" -> "notice/group_decrease";
            case "FRIEND_ADD" -> "notice/friend_add";
            case "FRIEND_DEL" -> "notice/friend_decrease";
            default -> null;
        };
        if (onebotType == null) {
            return 0L;
        }
        return this.repo.countEventsSince(instanceId, List.of(onebotType), sinceEpochSeconds);
    }

    public long countAllEvents(String instanceId) {
        return this.repo.countAllEvents(instanceId);
    }

    public void startBot(String instanceId, String envType) {
        log.info("[OneBot] \u542f\u7528\u673a\u5668\u4eba: selfId={}\uff08\u8fde\u63a5\u7531 WS \u5c42\u81ea\u52a8\u7ef4\u62a4\uff0c\u4ec5\u7f6e\u5728\u7ebf\uff09", (Object)instanceId);
        this.frameworkBotRepository.setStatus(this.platform(), instanceId, "ONLINE");
        this.repo.updateBotStatus(instanceId, "ONLINE");
    }

    public void stopBot(String instanceId) {
        Optional<OneBotSession> session = this.sessionRegistry.find(instanceId);
        session.ifPresent(s -> {
            try {
                s.close();
            }
            catch (Exception e) {
                log.warn("[OneBot] \u5173\u95ed\u4f1a\u8bdd\u5931\u8d25: selfId={}, {}", (Object)instanceId, (Object)e.getMessage());
            }
        });
        log.info("[OneBot] \u505c\u6b62\u673a\u5668\u4eba: selfId={}\uff08\u4f1a\u8bdd\u5df2\u65ad\u5f00\uff0c\u7f6e\u79bb\u7ebf\uff09", (Object)instanceId);
        this.frameworkBotRepository.setStatus(this.platform(), instanceId, "OFFLINE");
        this.repo.updateBotStatus(instanceId, "OFFLINE");
    }

    private static String str(Map<String, Object> row, String key) {
        Object v = row.get(key);
        if (v == null) {
            v = row.get(key.toLowerCase());
        }
        return v == null ? "" : String.valueOf(v);
    }

    @Generated
    public OneBotPlatformDataProvider(OneBotRepository repo, OneBotSessionRegistry sessionRegistry, FrameworkBotRepository frameworkBotRepository) {
        this.repo = repo;
        this.sessionRegistry = sessionRegistry;
        this.frameworkBotRepository = frameworkBotRepository;
    }
}


package dev.xuanji.adapter.qqbot.adapter;

import dev.xuanji.adapter.qqbot.model.Robot;
import dev.xuanji.adapter.qqbot.registry.RobotRegistry;
import dev.xuanji.api.adapter.Bot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * QqBotManager 作为 RobotRegistry 投影的契约。
 *
 * <p>锁死历史 bug：原实现自带一份从未被写入的 {@code Map}，
 * {@code all()} / {@code onlineCount()} 永远返回空和 0 —— 假数据比没数据更危险。
 */
@DisplayName("QqBotManager 视图")
class QqBotManagerViewTest {

    private static Robot robot(String appId, Integer status) {
        Robot r = new Robot();
        r.setId(appId);
        r.setAppId(appId);
        r.setStatus(status);
        r.setActiveEnv("PRODUCTION");
        return r;
    }

    private static QqBotManager managerWith(Robot... robots) {
        RobotRegistry registry = new RobotRegistry();
        for (Robot r : robots) registry.registerRobot(r);
        return new QqBotManager(registry);
    }

    @Test
    @DisplayName("all() 反映注册表真实内容，不再恒为空")
    void allReflectsRegistry() {
        List<Bot> bots = List.copyOf(managerWith(
                robot("102915166", 1),
                robot("102915167", 0)).all());

        assertEquals(2, bots.size());
        assertEquals("qq:102915166", bots.get(0).id());
        assertEquals("102915166", bots.get(0).selfId(), "selfId 必须是纯 appId，发送链路靠它查凭证");
        assertEquals("qq", bots.get(0).platform());
    }

    @Test
    @DisplayName("status==1 → ONLINE，其余 → OFFLINE")
    void statusMapsToOnlineFlag() {
        QqBotManager m = managerWith(
                robot("on", 1),
                robot("off", 0),
                robot("nul", null));

        assertTrue(m.find("on").orElseThrow().isOnline());
        assertFalse(m.find("off").orElseThrow().isOnline());
        assertFalse(m.find("nul").orElseThrow().isOnline());
        assertEquals(1, m.onlineCount());
    }

    @Test
    @DisplayName("find() 同时接受 appId 与 qq:appId 两种形态")
    void findAcceptsBothIdForms() {
        QqBotManager m = managerWith(robot("102915166", 1));

        assertTrue(m.find("102915166").isPresent());
        assertTrue(m.find("qq:102915166").isPresent());
        assertTrue(m.find("qq:nope").isEmpty());
        assertTrue(m.find(null).isEmpty());
        assertTrue(m.find("").isEmpty());
    }

    @Test
    @DisplayName("byPlatform 只认 qq，其他平台返回空")
    void byPlatformFiltersByPlatform() {
        QqBotManager m = managerWith(robot("a", 1));

        assertEquals(1, m.byPlatform("qq").size());
        assertTrue(m.byPlatform("onebot").isEmpty());
    }

    @Test
    @DisplayName("all() 顺序稳定（按 appId 排序），不受注册顺序影响")
    void allIsSorted() {
        List<String> first = managerWith(robot("zzz", 1), robot("aaa", 1)).all()
                .stream().map(Bot::selfId).toList();
        List<String> second = managerWith(robot("aaa", 1), robot("zzz", 1)).all()
                .stream().map(Bot::selfId).toList();

        assertEquals(List.of("aaa", "zzz"), first);
        assertEquals(first, second);
    }

    @Test
    @DisplayName("unregister 真的作用到注册表（不是操作一份影子 Map）")
    void unregisterHitsRegistry() {
        RobotRegistry registry = new RobotRegistry();
        registry.registerRobot(robot("102915166", 1));
        QqBotManager m = new QqBotManager(registry);

        m.unregister("qq:102915166");

        assertFalse(registry.hasRobot("102915166"));
        assertTrue(m.all().isEmpty());
    }

    @Test
    @DisplayName("botId 拼接口径与事件侧构造的 Bot 一致")
    void botIdFormat() {
        assertEquals("qq:102915166", QqBotManager.botId("102915166"));
        assertEquals("qq:unknown", QqBotManager.botId(null));
    }
}

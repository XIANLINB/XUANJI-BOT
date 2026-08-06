# 璇玑 XuanJi · 机器人框架

> 一个开箱即用的多平台机器人框架 —— QQ 官方 Bot · OneBot · 插件化 · 可视化控制台
>
> **小白也能用**：下载 → 双击启动 → 浏览器配置 → 完事。不需要看任何代码。

---

## ✨ 简介

璇玑（XuanJi）是基于 **Java 21 + Spring Boot** 的机器人开发框架，参考 AstrBot / OneBot 的设计理念：

- **多平台**：QQ 官方 Bot API（WebSocket / Webhook 双模式）+ OneBot（NapCat / Lagrange 等）
- **多机器人**：一个框架同时管理 N 个机器人，互相独立、各自落库
- **插件化**：PF4J 插件体系，`@Command` 注解一行注册命令，支持**热加载**（改插件不重启）
- **可视化控制台**：内置 Web 管理后台 —— 机器人管理、消息监控、健康指标、插件管理、权限管理、数据库浏览、运行日志，全中文界面
- **三级配置**：全局 / 机器人 / 群 三个粒度，改完即时生效

---

## 🚀 快速开始（小白向）

### 方式一：直接使用（无需 Java 环境）

1. 下载发布包（自带运行环境，解压即用，无需安装 JDK）
2. 双击 `启动.bat`（Windows）或运行 `./start.sh`（Linux/macOS）
3. 浏览器打开 **http://localhost:8668/xuanji/console/**
4. 首启引导 → 填入你的 QQ 机器人 AppID / AppSecret / Token → 保存
5. 完成！机器人自动上线，群里就能聊了

> 需要插件？把插件 `xxx.jar` 放进 `plugins/` 目录，控制台点「热加载」即可，**不用重启框架**。

### 方式二：源码运行（开发者）

```bash
# 环境：JDK 21+、Maven 3.9+
mvn clean package -DskipTests
cd xuanji-starter
java -jar target/xuanji-starter-1.0.0-SNAPSHOT.jar
```

IDEA 直接运行 `dev.xuanji.starter.XuanjiApplication` 主类。

### 控制台

| 页面 | 功能 |
|---|---|
| 仪表盘 | 机器人概览 / 消息趋势 / 平台状态 |
| 机器人 | 添加 / 删除机器人（WebSocket / Webhook） |
| 消息 | 群消息 / 私聊消息监控（收/发方向） |
| 事件 | 群事件 / 系统事件流 |
| 插件 | 插件列表 / 启停 / 绑定机器人 / **热加载** |
| 权限 | 主人管理 / 黑名单（群级+用户级） |
| 运行健康 | 熔断状态 / WebSocket 连接 / 慢阶段指标 |
| 运行设置 | 全局 / 机器人 / 群 三级配置 |
| 数据库 | 浏览框架库表 |
| 运行日志 | 实时日志查看 |

---

## 🧩 插件开发（开发者）

### 最小插件

```java
package my.plugin;

import dev.xuanji.api.annotation.*;
import dev.xuanji.api.plugin.XuanjiPluginBase;
import org.pf4j.PluginWrapper;

public class MyPlugin extends XuanjiPluginBase {

    public MyPlugin(PluginWrapper wrapper) { super(wrapper); }

    @XuanjiPlugin(id = "my-plugin", name = "我的插件", version = "1.0.0",
            author = "我", description = "示例插件", rateLimit = 0)
    public static class Commands {

        @Command("ping")
        public String ping() {
            return "pong!";
        }

        @Command(value = "hello", startWith = "hello")
        public String hello(@Arg("名字") String name) {
            return "你好, " + name + "!";
        }
    }
}
```

### @Command 语法糖

| 用法 | 说明 |
|---|---|
| `@Command("ping")` | 群聊 + 私聊都注册（BOTH） |
| `@Command(value="签到", scope=GROUP, at=AtMode.NEED)` | 仅群聊 + 必须 @机器人 触发 |
| `@Command(scope=GROUP, startWith="!")` | 感叹号前缀命令 |
| `@Command(scope=GROUP, roles={"owner","admin"})` | 权限过滤（群主/管理员） |
| `@Command(value="媒体", media=NEED, mediaTypes={IMAGE,VOICE,VIDEO})` | **媒体订阅**：纯图片/语音/视频消息无需命令词直接命中 |
| `@Arg("名字") String name` | 参数注入（自动剥掉命令词） |

### 事件 / 回复 API

```java
// 方法可注入：Bot、GroupMessageEvent、PrivateMessageEvent
@Command(value = "信息", scope = Command.Scope.GROUP)
public void botInfo(Bot bot) {
    bot.reply("群数量: " + bot.getGroupCount() + " 好友: " + bot.getUserCount());
}

// 富媒体回复
bot.replyMarkdown(Markdown.create().h2("标题").text("**加粗**").build());
bot.replyImage("https://example.com/pic.jpg");
bot.replyMarkdown(Markdown.create().text("带按钮").build(),
    Keyboard.create().row().btn("sign", "签到", "签到").endRow().build());

// 读取收到的媒体（框架已自动下载 → FILE_PATH 形态）
@Command(scope = Command.Scope.GROUP, media = MediaMode.NEED, mediaTypes = {MediaType.IMAGE})
public void onImage(GroupMessageEvent e, Bot bot) {
    for (MessageElement.Media m : e.getChain().medias()) {
        var ref = m.resolve(e.getPlatform());   // form: FILE_PATH / URL / ...
        bot.reply("媒体: " + ref.form() + " → " + ref.raw());
    }
}
```

### 打包与热加载

```bash
mvn package -pl xuanji-plugin-demo -am -DskipTests   # 打包（-am 必加：连带构建依赖）
Copy-Item xuanji-plugin-demo\target\*.jar plugins\   # 放入插件目录
```

控制台「插件」页 → **热加载** → 新代码生效，**无需重启框架**。

---

## 🗂 目录结构

```
xuanji/
├── xuanji-api/              # 插件 API（注解、消息元素、媒体五态）
├── xuanji-sdk/              # SDK 抽象（Bot / 事件）
├── xuanji-core/             # 核心内核（事件分发 / 插件管理 / 权限 / 存储）
├── xuanji-adapter-qqbot/    # QQ 官方适配器（WS + Webhook）
├── xuanji-adapter-onebot/   # OneBot 适配器
├── xuanji-console-server/   # 控制台后端 API
├── xuanji-console/          # 控制台前端（Vue3 + Naive UI）
├── xuanji-starter/          # 启动器（主类）
├── xuanji-plugin-demo/      # 演示插件
├── plugins/                 # 插件目录（.jar 放这里）
└── data/                    # 运行数据（数据库 / 媒体 / 配置，勿删）
```

---

## 📚 文档

| 文档 | 说明 |
|---|---|
| `docs/数据库升级兼容指南.md` | 升级不丢数据的机制与开发铁律 |
| `docs/acceptance-test-guide.md` | 验收测试指南 |
| `docs/permission-system.md` | 权限系统（主人/黑名单/等级矩阵） |
| `docs/onebot-napcat-guide.md` | OneBot + NapCat 接入指南 |
| `docs/xuanji-framework-full-design.md` | 框架完整设计 |
| `docs/qqbot-message-examples.md` | QQ 消息报文示例 |
| `docs/open/` | QQ 官方 API 文档（采集） |

---

## 📄 开源协议

[Apache License 2.0](LICENSE)

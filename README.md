# 璇玑 XuanJi · 机器人框架

> 🚀 一个开箱即用的 **多平台 · 多机器人 · 自带 AI 能力**的机器人框架
>
> QQ 官方 Bot · OneBot · 插件化 · 可视化控制台 · LLM 智能引擎

![Version](https://img.shields.io/badge/版本-v1.0.0-blue)
![Java](https://img.shields.io/badge/Java-25-orange)
![License](https://img.shields.io/badge/License-Apache%202.0-green)
![QQ群](https://img.shields.io/badge/QQ群-534445438-blue)

---

## 📢 交流群

有任何问题、建议或想一起交流，欢迎加入：

**QQ 群：534445438**

点击链接加入群聊【璇玑交流群】：https://qm.qq.com/q/BsOUz9HgPe

![QQ群二维码](assets/qrcode.png)

---

## ✨ 简介

璇玑（XuanJi）是一个基于 **Java 25 + Spring Boot 4** 的机器人开发框架，目标是「**小白能直接用，开发者好扩展，还能让 AI 真正会聊天**」：

- **多平台多机器人**：QQ 官方 Bot（WebSocket / Webhook 双模式）+ OneBot（NapCat / Lagrange 等），一个框架同时管理 N 个机器人，互相独立、各自落库
- **可视化控制台**：内置 Web 管理后台，机器人 / 消息 / 插件 / 权限 / 数据 / AI 全部可视化，全中文界面
- **AI 能力引擎**：内建 LLM 智能模块——AI 对话、人格角色扮演、长期记忆、工具调用、Agent 自主会话、知识库问答……让机器人不只是"查命令"，而是"会聊天、会办事"
- **插件化**：`@Command` 注解一行注册命令，支持**热加载**（改插件不重启）

---
## Java开发交流群：534445438，欢迎各位大神以及爱好者加入交流探讨！
---

## ⭐ 功能特性

### 框架核心

- ✅ 多平台适配：QQ 官方 API（WS + Webhook）+ OneBot v11（反向 WS）
- ✅ 多机器人：一个框架管理 N 个机器人，独立落库、独立配置
- ✅ 事件分发管线：统一事件模型 + 阶段化 Pipeline（鉴权 / 限速 / 去重 / 审核 / 分发）
- ✅ 三级配置：全局 / 机器人 / 群 三个粒度，改完即时生效
- ✅ 消息媒体：文本 / Markdown / 图片 / 语音 / 视频 / 文件 / Ark / 按钮，收发全支持
- ✅ 权限体系：主人 / 管理员 / 黑名单（群级 + 用户级）
- ✅ 数据存储：框架库 / 日志库 / 每机器人实例库 三级 H2，自动建表升级
- ✅ 备份恢复：在线导出 zip + 恢复前自动快照

### AI 能力（LLM 引擎）

- 🤖 **AI 对话**：群聊 @机器人 或全量接话，人格角色扮演（内置落落 / 清璃模版）
- 🧠 **长期记忆**：AI 记住你说过的话，跨重启不丢；记忆管理页可视化
- 🪪 **用户画像**：AI 认识常聊的人，自动提炼性格与喜好
- 💬 **主动搭话**：群冷场时主动活跃气氛（可配置，防骚扰）
- 🔧 **工具调用**：AI 会自己调工具（查时间 / 查天气 / 掷骰子 / 群统计……）
- 🧭 **意图路由**：人话 → 命令，说"帮我签到"自动执行
- 🕐 **自然语言建定时**："每周五 15:00 提醒我喝水" → 自动创建定时任务
- 🤝 **Agent 自主会话**：多步任务自主规划，跨消息保持上下文
- 🔌 **MCP 接入**：连接外部 MCP 服务，工具库无限扩展
- 📚 **知识库 RAG**：上传文档，AI 基于知识库回答问题
- 🖼 **多模态**：图片理解（GLM-4V）/ 文生图（CogView）/ 语音合成（TTS）/ 图文卡片渲染
- 🛡 **AI 审核**：消息过 LLM 审核，违规自动拦截

### 控制台

| 板块 | 功能 |
|---|---|
| 📊 仪表盘 | 机器人概览 / 消息趋势 / 运行状态 / 框架版本时间线 |
| 🤖 机器人 | 添加 / 管理机器人（WebSocket / Webhook） |
| 💬 消息监控 | 群消息 / 单聊消息实时监控 + 聊天窗口 |
| 👥 群聊 / 单聊管理 | 群列表 / 好友列表 / 成员管理 |
| 🧩 插件 | 插件列表 / 启停 / 热加载 / 插件市场 |
| ⏰ 定时任务 | 可视化 cron 任务管理 |
| 🔐 权限 / 安全 | 权限管理 / 黑名单 / 审计日志 / PIN 安全 |
| 🗄 数据 | 数据库浏览 / 备份恢复 |
| ⚙️ AI 能力 | 设置 / 对话 / 人格 / 用量 / 工具 / MCP / 知识库 / 审核 / 日报 / 记忆 / 供应商管理 |

---

## 🚀 快速开始

### 方式一：直接使用（无需 Java 环境）

1. 下载发布包（自带运行环境，解压即用，无需安装 JDK）
2. 双击 `启动.bat`（Windows）或运行 `./start.sh`（Linux / macOS）
3. 浏览器打开 **http://localhost:8668/xuanji/console/**
4. 首启引导 → 填入你的 QQ 机器人 AppID / AppSecret / Token → 保存
5. 完成！机器人自动上线，群里就能聊了

> 需要插件？把插件 `xxx.jar` 放进 `plugins/` 目录，控制台点「热加载」即可，**不用重启框架**。

### 方式二：源码运行（开发者）

```bash
# 环境：JDK 25+、Maven 3.9+
mvn clean package -DskipTests
cd xuanji-starter
java -jar target/xuanji-starter-1.0.0-SNAPSHOT.jar
```

IDEA 直接运行 `dev.xuanji.starter.XuanjiApplication` 主类。

### 打包发布

仓库提供开箱即用的打包脚本（`scripts/` 目录）：

| 脚本 | 产物 | 说明 |
|---|---|---|
| `build-playwright-windows.bat` / `.sh` | **Windows 专用包** | 含 Playwright 渲染（图文卡片），driver 只保留 Win 平台，体积最小 |
| `build-playwright-linux.bat` / `.sh` | **Linux 专用包** | 含 Playwright 渲染，driver 只保留 Linux 平台 |

详细用法见 `scripts/BUILD-USAGE.txt`。

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
├── xuanji-api/              # SPI 接口层（注解、消息元素、媒体、@LlmTool）
├── xuanji-sdk/              # SDK 抽象（Bot / 事件）
├── xuanji-core/             # 核心内核（事件分发 / 插件管理 / 权限 / 存储）
├── xuanji-llm/              # AI 能力引擎（LLM / 工具 / Agent / MCP / 记忆 / RAG / 多模态）
├── xuanji-scheduler/        # 定时任务中心
├── xuanji-adapter-qqbot/    # QQ 官方适配器（WS + Webhook）
├── xuanji-adapter-onebot/   # OneBot 适配器
├── xuanji-console-server/   # 控制台后端 API
├── xuanji-console/          # 控制台前端（Vue3 + Naive UI）
├── xuanji-starter/          # 启动器（主类）
├── xuanji-plugin-demo/      # 演示插件
├── plugins/                 # 插件目录（.jar 放这里）
├── scripts/                 # 打包 / 运维脚本
└── data/                    # 运行数据（数据库 / 媒体 / 配置，运行时自动生成）
```

---

## 📄 开源协议

[Apache License 2.0](LICENSE)

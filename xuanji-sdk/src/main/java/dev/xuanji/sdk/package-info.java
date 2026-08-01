/**
 * 璇玑插件开发 SDK。
 *
 * <h3>快速开始</h3>
 * <pre>{@code
 * <dependency>
 *     <groupId>dev.xuanji</groupId>
 *     <artifactId>xuanji-sdk</artifactId>
 *     <version>1.0.0</version>
 * </dependency>
 * }</pre>
 *
 * <h3>编写你的第一个插件</h3>
 * <pre>{@code
 * @XuanjiPlugin(id = "my-plugin", name = "我的插件")
 * public class MyPlugin {
 *
 *     @GroupMessage
 *     @MessageFilter(cmd = "hello")
 *     public String hello(@Arg("名字") String name) {
 *         return "你好, " + name + "!";
 *     }
 * }
 * }</pre>
 *
 * <p>SDK 提供了璇玑框架的全部 API：事件模型、消息链、指令注解、能力 SPI。
 * 它零 Spring 依赖，编译期即可完成插件开发。
 *
 * @see dev.xuanji.api.annotation.XuanjiPlugin
 * @see dev.xuanji.api.annotation.GroupMessage
 * @see dev.xuanji.api.annotation.MessageFilter
 * @see dev.xuanji.api.event.BotEvent
 * @see dev.xuanji.api.message.MessageChain
 */
package dev.xuanji.sdk;

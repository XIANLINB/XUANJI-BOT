package XuanJi.core.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 璇玑控制台 API 标记注解 —— 前后端交互接口的唯一入口标识。
 *
 * <p>凡是给控制台前端调用的 {@code @RestController}，都必须打上这个注解，
 * 并且 {@code @RequestMapping} 只写<b>业务相对路径</b>（如 {@code "/console"}、{@code "/db"}）。
 * 统一的版本化前缀 {@code /xuanji/api/v1} 由
 * {@code XuanJi.console.config.XuanJiApiRoutes} 在运行期集中拼接，
 * 各 controller 无需（也不应）自己书写。
 *
 * <h3>为什么放在 core</h3>
 * <p>控制台接口分散在三个模块：{@code xuanji-console-server}（控制台主体）、
 * {@code xuanji-starter}（setup / bot-config）、{@code xuanji-adapter-onebot}（onebot 配置）。
 * 三者唯一的公共上游是 {@code xuanji-core}，因此标记注解只能落在这里。
 * 本注解是零依赖的纯 JDK 注解，不引入任何 Web 技术栈，不会污染 core 的分层。
 *
 * <h3>不该打这个注解的场景</h3>
 * <ul>
 *   <li>协议入口 —— QQ 平台 Webhook 回调 {@code /webhook}、
 *       WebSocket 握手 {@code /api/v1/websocket}：路径由外部平台约定，改了就断连。</li>
 *   <li>静态资源 / SPA 转发类 controller。</li>
 * </ul>
 *
 * <h3>用法</h3>
 * <pre>{@code
 * @XuanJiApi
 * @RestController
 * @RequestMapping("/console")          // 实际暴露 /xuanji/api/v1/console/**
 * public class ConsoleBotController { ... }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface XuanJiApi {
}

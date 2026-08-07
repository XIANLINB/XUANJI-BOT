package dev.xuanji.console.config;

import dev.xuanji.core.web.XuanjiApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 璇玑控制台 API 路由总表 —— 前后端交互接口的<b>唯一前缀定义处</b>。
 *
 * <p>过去控制台接口的前缀散落在 12 个 controller 的 {@code @RequestMapping} 字符串里
 * （{@code /xuanji/api/console}、{@code /xuanji/api/db}、{@code /xuanji/api/setup}、
 * {@code /api/plugins} …），改版本、加鉴权、查路由都得全仓库 grep。
 * 现在改为：controller 只声明业务相对路径 + {@link XuanjiApi} 标记，
 * 版本化前缀在这里由 Spring 原生的 {@link PathMatchConfigurer#addPathPrefix} 统一注入。
 *
 * <h3>生效范围</h3>
 * <p>只对标注了 {@link XuanjiApi} 的 controller 生效。协议入口
 * （{@code /webhook}、{@code /api/v1/websocket}）没有该注解，路径保持原样不受影响。
 *
 * <h3>当前路由表</h3>
 * <pre>
 * /xuanji/api/v1/console/**             控制台总览 / 机器人 / 联系人 / 事件 / 监控 / 插件
 * /xuanji/api/v1/console/permission/**  主人与黑名单
 * /xuanji/api/v1/db/**                  数据库浏览
 * /xuanji/api/v1/setup/**               首次访问口令设置（鉴权白名单）
 * /xuanji/api/v1/bot-config/**          机器人增删改
 * /xuanji/api/v1/onebot/**              OneBot 适配器配置
 * </pre>
 *
 * <h3>升级到 v2 的做法</h3>
 * <p>改 {@link #API_VERSION} 一处即可，无需触碰任何 controller。
 *
 * @see XuanjiApi 标记注解
 */
@Slf4j
@Configuration
public class XuanjiApiRoutes implements WebMvcConfigurer {

    /** 控制台 API 命名空间（不含版本号），用于 404 归类等场景。 */
    public static final String API_NAMESPACE = "/xuanji/api/";

    /** 当前 API 版本号。升级只改这里。 */
    public static final String API_VERSION = "v1";

    /** 控制台 API 完整前缀：{@code /xuanji/api/v1}。 */
    public static final String API_PREFIX = API_NAMESPACE + API_VERSION;

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(API_PREFIX, type -> type.isAnnotationPresent(XuanjiApi.class));
        log.info("[路由] 控制台 API 统一前缀已装配: {}/** (标记注解 @XuanjiApi)", API_PREFIX);
    }

    /** 媒体静态映射：{@code /media/**} → {@code data/xuanji/media/}（文件存储页预览：图片/语音/视频直接播放）。 */
    @Override
    public void addResourceHandlers(org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry registry) {
        java.nio.file.Path media = java.nio.file.Paths.get("data", "xuanji", "media").toAbsolutePath().normalize();
        String location = media.toUri().toString();
        registry.addResourceHandler("/media/**")
                .addResourceLocations(location)
                .setCachePeriod(3600);
        log.info("[路由] 媒体静态映射: /media/** → {}", location);
    }
}

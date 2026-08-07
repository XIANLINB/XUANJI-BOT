package dev.xuanji.starter;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 控制台页面入口 — 只负责把浏览器导到前端 SPA 的 index.html。
 *
 * <p>前端产物打包在 {@code static/xuanji/console/} 下，采用 hash 路由，因此后端不需要为每个前端路由
 * 单独兜底，只要保证 {@code /xuanji/console} 能命中 index.html 即可。
 */
@Controller
public class ConsoleController {

    /** 控制台入口（带不带尾斜杠都接），forward 到静态资源避免多一次重定向。 */
    @GetMapping({"/xuanji/console", "/xuanji/console/"})
    public String console() {
        return "forward:/xuanji/console/index.html";
    }

    /** 根路径直接跳控制台，省得用户记完整路径。 */
    @GetMapping("/")
    public String home() {
        return "redirect:/xuanji/console";
    }
}

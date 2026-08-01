package dev.xuanji.starter;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ConsoleController {

    @GetMapping({"/xuanji/console", "/xuanji/console/"})
    public String console() {
        return "forward:/xuanji/console/index.html";
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/xuanji/console";
    }
}

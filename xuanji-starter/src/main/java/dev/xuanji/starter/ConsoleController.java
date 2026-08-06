/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.stereotype.Controller
 *  org.springframework.web.bind.annotation.GetMapping
 */
package dev.xuanji.starter;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ConsoleController {
    @GetMapping(value={"/xuanji/console", "/xuanji/console/"})
    public String console() {
        return "forward:/xuanji/console/index.html";
    }

    @GetMapping(value={"/"})
    public String home() {
        return "redirect:/xuanji/console";
    }
}


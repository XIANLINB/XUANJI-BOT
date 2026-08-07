package dev.xuanji.starter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 璇玑框架应用入口。
 *
 * <p>扫描整个 {@code dev.xuanji} 包，把 core / adapter / console-server 各模块的 Bean 一次性纳管；
 * 开启 {@link EnableScheduling} 供心跳、重连、统计等定时任务使用。
 */
@SpringBootApplication(scanBasePackages = "dev.xuanji")
@EnableScheduling
public class XuanjiApplication {

    public static void main(String[] args) {
        SpringApplication.run(XuanjiApplication.class, args);
    }
}

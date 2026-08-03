package dev.xuanji.starter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 璇玑 QQ 机器人框架启动类
 *
 * <p>Spring Boot 应用入口，启动后自动扫描并装配所有组件。
 */
@SpringBootApplication(scanBasePackages = "dev.xuanji")
@EnableScheduling
public class XuanjiApplication {

    public static void main(String[] args) {
        SpringApplication.run(XuanjiApplication.class, args);
    }
}

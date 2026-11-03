package com.tcg.bot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TcgBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(TcgBotApplication.class, args);
    }
}

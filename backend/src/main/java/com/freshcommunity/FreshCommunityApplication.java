package com.freshcommunity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FreshCommunityApplication {
    public static void main(String[] args) {
        SpringApplication.run(FreshCommunityApplication.class, args);
    }
}

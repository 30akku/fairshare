package com.anshi.fairshare;

import com.anshi.fairshare.config.FairShareProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAsync
@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties(FairShareProperties.class)
public class FairShareApplication {
    public static void main(String[] args) {
        SpringApplication.run(FairShareApplication.class, args);
    }
}


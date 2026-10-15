package com.riwi.skillbridge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling   // activa @Scheduled (job auto-COMPLETED)
public class SkillBridgeApplication {
    public static void main(String[] args) {
        SpringApplication.run(SkillBridgeApplication.class, args);
    }
}

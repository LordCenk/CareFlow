package com.careflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CareFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(CareFlowApplication.class, args);
    }
}

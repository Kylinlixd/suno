package com.suno.mall;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class RecycleMallApplication {

    public static void main(String[] args) {
        SpringApplication.run(RecycleMallApplication.class, args);
    }
}

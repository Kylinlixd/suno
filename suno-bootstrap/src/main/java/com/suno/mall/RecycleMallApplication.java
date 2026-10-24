package com.suno.mall;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.suno.mall.payment.AlipayProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@EnableConfigurationProperties(AlipayProperties.class)
@SpringBootApplication
public class RecycleMallApplication {

    public static void main(String[] args) {
        SpringApplication.run(RecycleMallApplication.class, args);
    }
}

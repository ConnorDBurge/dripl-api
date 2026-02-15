package com.dripl;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class DriplApplication {

    public static void main(String[] args) {
        SpringApplication.run(DriplApplication.class, args);
    }
}

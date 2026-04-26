package com.raditomo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class RaditomoApplication {

    public static void main(String[] args) {
        SpringApplication.run(RaditomoApplication.class, args);
    }
}

package com.ob.ohlc_cache;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OhlcCacheApplication {

    public static void main(String[] args) {
        SpringApplication.run(OhlcCacheApplication.class, args);
    }

}

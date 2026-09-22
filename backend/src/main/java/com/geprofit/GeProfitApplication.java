package com.geprofit;

import com.geprofit.config.GeProfitProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(GeProfitProperties.class)
public class GeProfitApplication {

    public static void main(String[] args) {
        SpringApplication.run(GeProfitApplication.class, args);
    }
}

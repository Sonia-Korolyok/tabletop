package com.tabletop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TableTopApplication {
    public static void main(String[] args) {
        SpringApplication.run(TableTopApplication.class, args);
    }
}

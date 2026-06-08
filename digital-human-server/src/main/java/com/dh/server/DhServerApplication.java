package com.dh.server;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.dh.server.storage.mapper")
public class DhServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(DhServerApplication.class, args);
    }
}

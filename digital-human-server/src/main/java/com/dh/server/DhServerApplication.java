package com.dh.server;

import com.dh.server.admin.AdminAuthFilter;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.dh.server.storage.mapper")
@EnableScheduling
public class DhServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(DhServerApplication.class, args);
    }

    @Bean
    public FilterRegistrationBean<AdminAuthFilter> adminAuthFilter() {
        FilterRegistrationBean<AdminAuthFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new AdminAuthFilter());
        bean.addUrlPatterns("/api/admin/*");
        return bean;
    }
}

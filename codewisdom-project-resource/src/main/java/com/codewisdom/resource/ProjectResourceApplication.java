package com.codewisdom.resource;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * codewisdom-project-resource 服务启动类。
 *
 * <p>扫描范围包含 {@code com.codewisdom.common}，以装配统一响应体与全局异常处理。
 */
@SpringBootApplication(scanBasePackages = {"com.codewisdom.resource", "com.codewisdom.common"})
@MapperScan("com.codewisdom.resource.mapper")
public class ProjectResourceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProjectResourceApplication.class, args);
    }
}

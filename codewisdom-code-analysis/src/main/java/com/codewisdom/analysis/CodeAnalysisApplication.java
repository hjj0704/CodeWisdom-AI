package com.codewisdom.analysis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * codewisdom-code-analysis 服务启动类。
 *
 * <p>扫描范围包含 {@code com.codewisdom.common}，以装配统一响应体与全局异常处理。
 */
@SpringBootApplication(scanBasePackages = {"com.codewisdom.analysis", "com.codewisdom.common"})
public class CodeAnalysisApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeAnalysisApplication.class, args);
    }
}

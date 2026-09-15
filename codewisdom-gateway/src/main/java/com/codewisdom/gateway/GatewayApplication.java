package com.codewisdom.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 统一网关启动类（WebFlux）。
 *
 * <p>注意：网关是响应式应用，<b>不扫描</b> {@code com.codewisdom.common}，
 * 因为其中的 {@code GlobalExceptionHandler} 依赖 Spring MVC，会造成运行时类加载失败。
 * 网关的错误处理走 {@code GatewayErrorWebExceptionHandler}（阶段 1B 实现）。
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}

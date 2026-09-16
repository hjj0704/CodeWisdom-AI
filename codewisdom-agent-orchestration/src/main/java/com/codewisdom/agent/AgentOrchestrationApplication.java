package com.codewisdom.agent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * codewisdom-agent-orchestration 服务启动类。
 *
 * <p>扫描范围包含 {@code com.codewisdom.common}，以装配统一响应体与全局异常处理。
 */
@SpringBootApplication(scanBasePackages = {"com.codewisdom.agent", "com.codewisdom.common"})
@MapperScan("com.codewisdom.agent.mapper")
public class AgentOrchestrationApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentOrchestrationApplication.class, args);
    }
}

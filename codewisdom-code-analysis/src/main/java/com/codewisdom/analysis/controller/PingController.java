package com.codewisdom.analysis.controller;

import com.codewisdom.common.api.R;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 存活探针。用于网关路由连通性验证与容器健康检查。
 */
@RestController
public class PingController {

    @Value("${spring.application.name}")
    private String applicationName;

    @GetMapping("/ping")
    public R<String> ping() {
        return R.ok(applicationName);
    }
}

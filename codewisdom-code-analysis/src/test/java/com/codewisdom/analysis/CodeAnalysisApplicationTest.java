package com.codewisdom.analysis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 骨架验收：Spring 上下文可加载，且 /ping 返回统一响应体。
 *
 * <p><b>{@code @ActiveProfiles("test")} 从 T-105 起必需</b>：本服务接入了
 * MyBatis-Plus + Flyway，classpath 上有了数据源自动配置。不打这个 profile 的话，
 * 上下文会去找一个并不存在的 MySQL 而启动失败——**而正确的结果不是「跳过数据源」，
 * 是走 H2 测试档**：那样连建表脚本都真的跑了一遍。
 *
 * <p>因此本测试在**没有 Docker / MySQL** 的机器上依然可跑，这是刻意保持的性质：
 * 417 个测试的基线不依赖任何中间件（见 R-13 与测试档注释）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CodeAnalysisApplicationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("上下文加载成功且 /ping 返回统一响应体")
    void contextLoadsAndPingResponds() throws Exception {
        mockMvc.perform(get("/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value("codewisdom-code-analysis"));
    }
}

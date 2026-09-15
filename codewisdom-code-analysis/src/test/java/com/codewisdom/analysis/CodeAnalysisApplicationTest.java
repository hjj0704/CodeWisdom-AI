package com.codewisdom.analysis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 骨架验收：Spring 上下文可加载（不依赖任何中间件），且 /ping 返回统一响应体。
 */
@SpringBootTest
@AutoConfigureMockMvc
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

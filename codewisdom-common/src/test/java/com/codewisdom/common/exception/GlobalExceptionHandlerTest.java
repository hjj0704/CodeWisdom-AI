package com.codewisdom.common.exception;

import com.codewisdom.common.api.ErrorCode;
import com.codewisdom.common.api.R;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 全局异常处理器三态验收：成功分支、业务异常分支、未知异常分支。
 */
@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("成功分支：HTTP 200 且 code=0")
    void successBranch() throws Exception {
        mockMvc.perform(get("/probe/ok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value("hello"));
    }

    @Test
    @DisplayName("业务异常分支：返回错误码与可读文案，HTTP 200")
    void bizExceptionBranch() throws Exception {
        mockMvc.perform(get("/probe/biz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(51000))
                .andExpect(jsonPath("$.message").value("仓库不可达"));
    }

    @Test
    @DisplayName("未知异常分支：兜底为 SYSTEM_ERROR 且不泄露内部细节")
    void unexpectedBranchDoesNotLeakInternals() throws Exception {
        mockMvc.perform(get("/probe/boom"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(50001))
                .andExpect(jsonPath("$.message").value("系统内部错误"))
                // 关键安全断言：堆栈信息与内部异常文案不得出现在响应中
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("connection refused"))));
    }

    @RestController
    static class ProbeController {

        @GetMapping("/probe/ok")
        R<String> ok() {
            return R.ok("hello");
        }

        @GetMapping("/probe/biz")
        R<Void> biz() {
            throw new BizException(ErrorCode.IMPORT_ERROR, "仓库不可达");
        }

        @GetMapping("/probe/boom")
        R<Void> boom() {
            throw new IllegalStateException("connection refused to internal-db:3306");
        }
    }
}

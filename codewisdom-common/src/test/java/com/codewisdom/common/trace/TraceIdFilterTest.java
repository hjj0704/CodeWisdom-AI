package com.codewisdom.common.trace;

import com.codewisdom.common.api.R;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("TraceIdFilter")
class TraceIdFilterTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .addFilters(new TraceIdFilter())
                .build();
    }

    @AfterEach
    void tearDown() {
        TraceIdHolder.clear();
    }

    @Test
    @DisplayName("上游未传 TraceId 时自动生成，并回写响应头与响应体")
    void generatesTraceIdWhenAbsent() throws Exception {
        MvcResult result = mockMvc.perform(get("/probe/trace"))
                .andExpect(status().isOk())
                .andReturn();

        String responseTraceId = result.getResponse().getHeader(TraceIdHolder.TRACE_HEADER);
        assertThat(responseTraceId).isNotBlank();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains(responseTraceId);
    }

    @Test
    @DisplayName("上游传入 TraceId 时原样复用，保证跨服务链路一致")
    void reusesIncomingTraceId() throws Exception {
        mockMvc.perform(get("/probe/trace").header(TraceIdHolder.TRACE_HEADER, "upstream-abc123"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdHolder.TRACE_HEADER, "upstream-abc123"))
                .andExpect(jsonPath("$.traceId").value("upstream-abc123"));
    }

    @Test
    @DisplayName("请求结束后清理 MDC，避免线程池复用串号")
    void clearsMdcAfterRequest() throws Exception {
        mockMvc.perform(get("/probe/trace")).andExpect(status().isOk());

        assertThat(TraceIdHolder.get()).isNull();
    }

    @Test
    @DisplayName("上游传入空串时退化为自动生成")
    void blankIncomingTraceIdFallsBackToGenerated() throws Exception {
        MvcResult result = mockMvc.perform(get("/probe/trace")
                        .header(TraceIdHolder.TRACE_HEADER, "   "))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getHeader(TraceIdHolder.TRACE_HEADER)).isNotBlank();
    }

    @RestController
    static class ProbeController {

        @GetMapping("/probe/trace")
        R<String> trace() {
            return R.ok("ok");
        }
    }
}

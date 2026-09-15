package com.codewisdom.common.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("R 统一响应体")
class RTest {

    @Test
    @DisplayName("ok() 返回成功码且 data 为空")
    void okWithoutData() {
        R<Void> r = R.ok();

        assertThat(r.getCode()).isZero();
        assertThat(r.getMessage()).isEqualTo(ErrorCode.SUCCESS.message());
        assertThat(r.getData()).isNull();
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getTimestamp()).isPositive();
    }

    @Test
    @DisplayName("ok(data) 携带数据且判定为成功")
    void okWithData() {
        R<String> r = R.ok("payload");

        assertThat(r.getCode()).isZero();
        assertThat(r.getData()).isEqualTo("payload");
        assertThat(r.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("fail(ErrorCode) 使用错误码自带文案")
    void failWithErrorCode() {
        R<Void> r = R.fail(ErrorCode.IMPORT_ERROR);

        assertThat(r.getCode()).isEqualTo(51000);
        assertThat(r.getMessage()).isEqualTo("项目导入失败");
        assertThat(r.getData()).isNull();
        assertThat(r.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("fail(ErrorCode, message) 覆盖为自定义文案")
    void failWithCustomMessage() {
        R<Void> r = R.fail(ErrorCode.ARCHIVE_PATH_TRAVERSAL, "检测到非法路径: ../etc/passwd");

        assertThat(r.getCode()).isEqualTo(51003);
        assertThat(r.getMessage()).contains("../etc/passwd");
    }

    @Test
    @DisplayName("无请求上下文时 traceId 为空——由 TraceIdFilter 在请求内注入")
    void traceIdIsNullOutsideRequestScope() {
        R<Void> r = R.ok();

        // 契约：R 只负责读取当前 MDC，不主动生成，避免在非请求线程污染 MDC。
        // 请求内的注入与清理由 TraceIdFilter 负责，见 TraceIdFilterTest。
        assertThat(r.getTraceId()).isNull();
    }
}

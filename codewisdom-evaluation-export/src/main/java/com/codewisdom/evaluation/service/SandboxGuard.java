package com.codewisdom.evaluation.service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 沙箱安全边界（T-804）：拒绝明显危险命令，MVP 不执行用户代码。
 */
public class SandboxGuard {

    private static final List<Pattern> DENIED_PATTERNS = List.of(
            Pattern.compile("rm\\s+-rf", Pattern.CASE_INSENSITIVE),
            Pattern.compile(":\\(\\)\\s*\\{", Pattern.CASE_INSENSITIVE),
            Pattern.compile("nc\\s+-", Pattern.CASE_INSENSITIVE),
            Pattern.compile("curl\\s+.*\\|\\s*sh", Pattern.CASE_INSENSITIVE),
            Pattern.compile("wget\\s+.*\\|\\s*sh", Pattern.CASE_INSENSITIVE),
            Pattern.compile("powershell\\s+.*-enc", Pattern.CASE_INSENSITIVE));

    public boolean isAllowed(String command) {
        if (command == null || command.isBlank()) {
            return false;
        }
        String normalized = command.toLowerCase(Locale.ROOT);
        for (Pattern pattern : DENIED_PATTERNS) {
            if (pattern.matcher(normalized).find()) {
                return false;
            }
        }
        return true;
    }

    public void assertAllowed(String command) {
        if (!isAllowed(command)) {
            throw new SandboxRejectedException("沙箱拒绝执行: " + command);
        }
    }

    public static class SandboxRejectedException extends RuntimeException {
        public SandboxRejectedException(String message) {
            super(message);
        }
    }
}

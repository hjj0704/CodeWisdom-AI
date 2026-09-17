package com.codewisdom.evaluation.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 沙箱安全边界（T-804）：命令校验 + 策略限额（MVP 不执行用户代码）。
 */
@Component
public class SandboxGuard {

    public static final int MAX_COMMAND_LENGTH = 4096;
    public static final int MAX_EXECUTION_SECONDS = 30;
    public static final int MAX_MEMORY_MB = 256;

    private static final Pattern REMOTE_URL = Pattern.compile("https?://[^\\s\"']+", Pattern.CASE_INSENSITIVE);
    private static final Pattern LOCAL_HOST = Pattern.compile("https?://(localhost|127\\.0\\.0\\.1)([:/]|$)",
            Pattern.CASE_INSENSITIVE);

    private static final List<Pattern> DENIED_PATTERNS = List.of(
            Pattern.compile("rm\\s+-rf", Pattern.CASE_INSENSITIVE),
            Pattern.compile(":\\(\\)\\s*\\{", Pattern.CASE_INSENSITIVE),
            Pattern.compile("nc\\s+-", Pattern.CASE_INSENSITIVE),
            Pattern.compile("curl\\s+.*\\|\\s*sh", Pattern.CASE_INSENSITIVE),
            Pattern.compile("wget\\s+.*\\|\\s*sh", Pattern.CASE_INSENSITIVE),
            Pattern.compile("powershell\\s+.*-enc", Pattern.CASE_INSENSITIVE),
            Pattern.compile("mkfs\\.", Pattern.CASE_INSENSITIVE),
            Pattern.compile("dd\\s+if=", Pattern.CASE_INSENSITIVE),
            Pattern.compile("chmod\\s+777", Pattern.CASE_INSENSITIVE),
            Pattern.compile(">/dev/sd", Pattern.CASE_INSENSITIVE),
            Pattern.compile("bash\\s+-i", Pattern.CASE_INSENSITIVE));

    public record SandboxPolicy(
            int maxExecutionSeconds,
            int maxMemoryMb,
            int maxCommandLength,
            boolean networkIsolation
    ) {
        public static SandboxPolicy defaults() {
            return new SandboxPolicy(MAX_EXECUTION_SECONDS, MAX_MEMORY_MB, MAX_COMMAND_LENGTH, true);
        }
    }

    public record SandboxCheckResult(
            boolean allowed,
            String message,
            SandboxPolicy policy
    ) {
    }

    public boolean isAllowed(String command) {
        return check(command).allowed();
    }

    public SandboxCheckResult check(String command) {
        SandboxPolicy policy = SandboxPolicy.defaults();
        if (command == null || command.isBlank()) {
            return denied("命令不能为空", policy);
        }
        if (command.length() > MAX_COMMAND_LENGTH) {
            return denied("命令长度超过沙箱上限 " + MAX_COMMAND_LENGTH, policy);
        }
        String normalized = command.toLowerCase(Locale.ROOT);
        for (Pattern pattern : DENIED_PATTERNS) {
            if (pattern.matcher(normalized).find()) {
                return denied("沙箱拒绝执行: 命中危险模式", policy);
            }
        }
        if (violatesNetworkIsolation(command)) {
            return denied("沙箱网络隔离: 禁止访问外网 URL（仅允许 localhost）", policy);
        }
        return new SandboxCheckResult(true, "命令通过沙箱策略校验（未实际执行）", policy);
    }

    public void assertAllowed(String command) {
        SandboxCheckResult result = check(command);
        if (!result.allowed()) {
            throw new SandboxRejectedException(result.message());
        }
    }

    private static SandboxCheckResult denied(String message, SandboxPolicy policy) {
        return new SandboxCheckResult(false, message, policy);
    }

    private static boolean violatesNetworkIsolation(String command) {
        Matcher matcher = REMOTE_URL.matcher(command);
        while (matcher.find()) {
            String url = matcher.group();
            if (!LOCAL_HOST.matcher(url).find()) {
                return true;
            }
        }
        return false;
    }

    public static class SandboxRejectedException extends RuntimeException {
        public SandboxRejectedException(String message) {
            super(message);
        }
    }
}

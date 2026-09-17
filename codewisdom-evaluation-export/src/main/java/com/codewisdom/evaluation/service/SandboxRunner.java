package com.codewisdom.evaluation.service;

import com.codewisdom.common.api.ErrorCode;
import com.codewisdom.common.exception.BizException;
import com.codewisdom.evaluation.config.WorkspaceProperties;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 轻量项目沙箱演示执行（白名单命令，超时截断）。
 */
@Service
public class SandboxRunner {

    private static final List<Pattern> ALLOWED_COMMANDS = List.of(
            Pattern.compile("^mvn\\s+-q\\s+(-DskipTests\\s+)?compile$"),
            Pattern.compile("^mvn\\s+-q\\s+(-DskipTests\\s+)?test$"),
            Pattern.compile("^mvn\\s+-q\\s+(-DskipTests\\s+)?package$"),
            Pattern.compile("^java\\s+-version$"));

    private final WorkspaceProperties workspaceProperties;
    private final SandboxGuard sandboxGuard;

    public SandboxRunner(WorkspaceProperties workspaceProperties, SandboxGuard sandboxGuard) {
        this.workspaceProperties = workspaceProperties;
        this.sandboxGuard = sandboxGuard;
    }

    public record SandboxRunResult(
            boolean success,
            int exitCode,
            String stdout,
            String stderr,
            long durationMs,
            String message
    ) {
    }

    public SandboxRunResult run(long projectId, String command) {
        SandboxGuard.SandboxCheckResult check = sandboxGuard.check(command);
        if (!check.allowed()) {
            return new SandboxRunResult(false, -1, "", "", 0, check.message());
        }
        String normalized = command.trim();
        if (!isAllowlisted(normalized)) {
            return new SandboxRunResult(false, -1, "", "",
                    0, "演示仅支持：mvn -q compile/test/package、java -version");
        }

        Path workDir = resolveWorkDir(projectId);
        if (!Files.isDirectory(workDir)) {
            throw BizException.of(ErrorCode.NOT_FOUND, "项目工作区不存在");
        }

        long started = System.currentTimeMillis();
        ProcessBuilder builder = new ProcessBuilder();
        builder.command(shellCommand(normalized));
        builder.directory(workDir.toFile());
        builder.redirectErrorStream(false);

        try {
            Process process = builder.start();
            boolean finished = process.waitFor(SandboxGuard.MAX_EXECUTION_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new SandboxRunResult(false, -1, readStream(process.getInputStream()),
                        readStream(process.getErrorStream()),
                        System.currentTimeMillis() - started,
                        "执行超时（>" + SandboxGuard.MAX_EXECUTION_SECONDS + "s）");
            }
            int code = process.exitValue();
            String out = readStream(process.getInputStream());
            String err = readStream(process.getErrorStream());
            long duration = System.currentTimeMillis() - started;
            String msg = code == 0 ? "演示执行完成" : "命令退出码 " + code;
            return new SandboxRunResult(code == 0, code, out, err, duration, msg);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw BizException.of(ErrorCode.SYSTEM_ERROR, "沙箱执行失败: " + e.getMessage(), e);
        }
    }

    private static boolean isAllowlisted(String command) {
        String compact = command.replaceAll("\\s+", " ").trim();
        for (Pattern pattern : ALLOWED_COMMANDS) {
            if (pattern.matcher(compact).matches()) {
                return true;
            }
        }
        return false;
    }

    private Path resolveWorkDir(long projectId) {
        Path workspace = Paths.get(workspaceProperties.getRoot()).resolve(String.valueOf(projectId));
        Path repoDir = workspace.resolve("repo");
        return Files.isDirectory(repoDir) ? repoDir : workspace;
    }

    private static List<String> shellCommand(String commandLine) {
        if (isWindows()) {
            return List.of("cmd.exe", "/c", commandLine);
        }
        return List.of("sh", "-c", commandLine);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static String readStream(java.io.InputStream stream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(line);
                if (sb.length() > 12000) {
                    sb.append("\n...(输出已截断)");
                    break;
                }
            }
            return sb.toString();
        }
    }
}

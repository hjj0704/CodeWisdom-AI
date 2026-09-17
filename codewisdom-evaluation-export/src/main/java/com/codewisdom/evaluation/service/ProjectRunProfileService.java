package com.codewisdom.evaluation.service;

import com.codewisdom.evaluation.config.WorkspaceProperties;
import com.codewisdom.evaluation.domain.RunCapability;
import com.codewisdom.evaluation.dto.RunProfileView;
import com.codewisdom.common.api.ErrorCode;
import com.codewisdom.common.exception.BizException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class ProjectRunProfileService {

    private static final Pattern ARTIFACT_ID = Pattern.compile("<artifactId>([^<]+)</artifactId>");

    private final WorkspaceProperties workspaceProperties;
    private final RunCapabilityJudge runCapabilityJudge;
    private final DeployGuideGenerator deployGuideGenerator;

    public ProjectRunProfileService(WorkspaceProperties workspaceProperties,
                                    RunCapabilityJudge runCapabilityJudge,
                                    DeployGuideGenerator deployGuideGenerator) {
        this.workspaceProperties = workspaceProperties;
        this.runCapabilityJudge = runCapabilityJudge;
        this.deployGuideGenerator = deployGuideGenerator;
    }

    public RunProfileView profile(long projectId) {
        Path scanRoot = scanRoot(projectId);
        if (!Files.isDirectory(scanRoot)) {
            throw BizException.of(ErrorCode.NOT_FOUND, "项目工作区不存在: " + projectId);
        }

        List<String> hints = collectDependencyHints(scanRoot);
        RunCapability capability = runCapabilityJudge.judge(hints);
        List<String> middleware = filterMiddleware(hints);
        String guide = deployGuideGenerator.generate("项目 #" + projectId, middleware);
        String summary = capability == RunCapability.HEAVY
                ? "检测到中间件依赖，建议本地部署运行，网页沙箱不适用"
                : "未检测到重型中间件依赖，支持沙箱校验与在线演示（mvn compile/test）";

        return new RunProfileView(capability.name(), middleware, guide, summary);
    }

    private List<String> collectDependencyHints(Path scanRoot) {
        Set<String> hints = new LinkedHashSet<>();
        try (Stream<Path> walk = Files.walk(scanRoot)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals("pom.xml")
                            || p.getFileName().toString().equals("requirements.txt"))
                    .forEach(p -> {
                        try {
                            String content = Files.readString(p, StandardCharsets.UTF_8);
                            Matcher matcher = ARTIFACT_ID.matcher(content);
                            while (matcher.find()) {
                                hints.add(matcher.group(1));
                            }
                            for (String line : content.split("\n")) {
                                String trimmed = line.trim();
                                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                                    hints.add(trimmed);
                                }
                            }
                        } catch (IOException ignored) {
                            // skip
                        }
                    });
        } catch (IOException e) {
            throw BizException.of(ErrorCode.PARSE_ERROR, "扫描依赖声明失败: " + e.getMessage(), e);
        }
        return new ArrayList<>(hints);
    }

    private static List<String> filterMiddleware(List<String> hints) {
        Set<String> heavyMarkers = Set.of(
                "redis", "mysql", "mariadb", "postgresql", "postgres",
                "rabbitmq", "spring-rabbit", "kafka", "nacos", "mongodb",
                "elasticsearch", "minio", "rocketmq", "activemq");
        List<String> found = new ArrayList<>();
        for (String hint : hints) {
            String normalized = hint.toLowerCase(Locale.ROOT);
            for (String marker : heavyMarkers) {
                if (normalized.contains(marker) && !found.contains(marker)) {
                    found.add(marker);
                }
            }
        }
        return found;
    }

    private Path scanRoot(long projectId) {
        Path workspace = Paths.get(workspaceProperties.getRoot()).resolve(String.valueOf(projectId));
        Path repoDir = workspace.resolve("repo");
        if (Files.isDirectory(repoDir)) {
            return repoDir;
        }
        return workspace;
    }
}

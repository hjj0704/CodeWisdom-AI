package com.codewisdom.analysis.service;

import com.codewisdom.analysis.arch.ArchRiskAnalyzer;
import com.codewisdom.analysis.arch.AuditEngine;
import com.codewisdom.analysis.arch.AuditPipeline;
import com.codewisdom.analysis.arch.LayerDetector;
import com.codewisdom.analysis.arch.PomConflictAnalyzer;
import com.codewisdom.analysis.arch.RequirementsConflictAnalyzer;
import com.codewisdom.analysis.config.WorkspaceProperties;
import com.codewisdom.analysis.domain.AuditIssue;
import com.codewisdom.analysis.domain.AuditReport;
import com.codewisdom.analysis.domain.LayerReport;
import com.codewisdom.analysis.domain.TypeDeclaration;
import com.codewisdom.analysis.dto.AuditReportView;
import com.codewisdom.analysis.parser.CallGraph;
import com.codewisdom.analysis.parser.JavaDependencyExtractor;
import com.codewisdom.analysis.parser.JavaStructureExtractor;
import com.codewisdom.analysis.parser.ParseHandle;
import com.codewisdom.analysis.parser.SourceParser;
import com.codewisdom.common.api.ErrorCode;
import com.codewisdom.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Service
public class ProjectAuditService {

    private static final int MAX_JAVA_FILES = 120;

    private final WorkspaceProperties workspaceProperties;
    private final AuditEngine auditEngine;
    private final AuditPipeline auditPipeline;
    private final PomConflictAnalyzer pomConflictAnalyzer;
    private final RequirementsConflictAnalyzer requirementsConflictAnalyzer;
    private final ArchRiskAnalyzer archRiskAnalyzer;
    private final LayerDetector layerDetector;
    private final SourceParser sourceParser;
    private final JavaStructureExtractor structureExtractor;
    private final JavaDependencyExtractor dependencyExtractor;
    private final AuditIssuePersistenceService auditIssuePersistenceService;

    public ProjectAuditService(WorkspaceProperties workspaceProperties,
                               AuditEngine auditEngine,
                               AuditPipeline auditPipeline,
                               PomConflictAnalyzer pomConflictAnalyzer,
                               RequirementsConflictAnalyzer requirementsConflictAnalyzer,
                               ArchRiskAnalyzer archRiskAnalyzer,
                               LayerDetector layerDetector,
                               SourceParser sourceParser,
                               JavaStructureExtractor structureExtractor,
                               JavaDependencyExtractor dependencyExtractor,
                               AuditIssuePersistenceService auditIssuePersistenceService) {
        this.workspaceProperties = workspaceProperties;
        this.auditEngine = auditEngine;
        this.auditPipeline = auditPipeline;
        this.pomConflictAnalyzer = pomConflictAnalyzer;
        this.requirementsConflictAnalyzer = requirementsConflictAnalyzer;
        this.archRiskAnalyzer = archRiskAnalyzer;
        this.layerDetector = layerDetector;
        this.sourceParser = sourceParser;
        this.structureExtractor = structureExtractor;
        this.dependencyExtractor = dependencyExtractor;
        this.auditIssuePersistenceService = auditIssuePersistenceService;
    }

    @Transactional
    public AuditReportView auditProject(long projectId) {
        Path scanRoot = scanRoot(projectId);
        if (!Files.isDirectory(scanRoot)) {
            throw BizException.of(ErrorCode.NOT_FOUND, "项目工作区不存在: " + projectId);
        }

        List<AuditIssue> issues = new ArrayList<>();
        issues.addAll(analyzePoms(scanRoot));
        JavaScanResult javaScan = scanJava(scanRoot);
        issues.addAll(javaScan.issues);
        issues.addAll(analyzeArchitecture(javaScan));
        issues.addAll(analyzeRequirements(scanRoot));

        AuditReport report = auditPipeline.aggregate(issues);
        auditIssuePersistenceService.replaceProjectIssues(projectId, report.issues());
        List<AuditReportView.AuditIssueItem> items = report.issues().stream()
                .map(AuditReportView.AuditIssueItem::from)
                .toList();
        return new AuditReportView(
                report.total(),
                report.countByRiskLevel().get(AuditIssue.RiskLevel.HIGH).intValue(),
                report.countByRiskLevel().get(AuditIssue.RiskLevel.MEDIUM).intValue(),
                report.countByRiskLevel().get(AuditIssue.RiskLevel.LOW).intValue(),
                items,
                javaScan.totalJavaFiles,
                javaScan.scannedJavaFiles,
                javaScan.parseSkipped,
                javaScan.totalJavaFiles > MAX_JAVA_FILES,
                buildScanNote(javaScan));
    }

    private List<AuditIssue> analyzePoms(Path scanRoot) {
        Map<String, String> poms = new LinkedHashMap<>();
        try (Stream<Path> walk = Files.walk(scanRoot)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals("pom.xml"))
                    .forEach(p -> {
                        try {
                            String rel = scanRoot.relativize(p).toString().replace('\\', '/');
                            poms.put(rel, Files.readString(p, StandardCharsets.UTF_8));
                        } catch (IOException ignored) {
                            // skip unreadable pom
                        }
                    });
        } catch (IOException e) {
            throw BizException.of(ErrorCode.PARSE_ERROR, "扫描 pom 失败: " + e.getMessage(), e);
        }
        return pomConflictAnalyzer.analyze(poms);
    }

    private List<AuditIssue> analyzeRequirements(Path scanRoot) {
        Map<String, String> requirementFiles = new LinkedHashMap<>();
        try (Stream<Path> walk = Files.walk(scanRoot)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        return name.equals("requirements.txt") || name.endsWith("-requirements.txt");
                    })
                    .forEach(p -> {
                        try {
                            String rel = scanRoot.relativize(p).toString().replace('\\', '/');
                            requirementFiles.put(rel, Files.readString(p, StandardCharsets.UTF_8));
                        } catch (IOException ignored) {
                            // skip unreadable requirements
                        }
                    });
        } catch (IOException e) {
            throw BizException.of(ErrorCode.PARSE_ERROR, "扫描 requirements 失败: " + e.getMessage(), e);
        }
        if (requirementFiles.isEmpty()) {
            return List.of();
        }
        return requirementsConflictAnalyzer.analyze(requirementFiles);
    }

    private List<AuditIssue> analyzeArchitecture(JavaScanResult javaScan) {
        if (javaScan.typesByPath.isEmpty()) {
            return List.of();
        }
        LayerReport layerReport = layerDetector.detect(javaScan.typesByPath);
        CallGraph callGraph = CallGraph.build(javaScan.units);
        return archRiskAnalyzer.analyze(layerReport, callGraph, javaScan.typesByPath);
    }

    private JavaScanResult scanJava(Path scanRoot) {
        List<AuditIssue> issues = new ArrayList<>();
        Map<String, List<TypeDeclaration>> typesByPath = new LinkedHashMap<>();
        List<CallGraph.SourceUnit> units = new ArrayList<>();
        int parseSkipped = 0;
        try (Stream<Path> walk = Files.walk(scanRoot)) {
            List<Path> allJavaFiles = walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .sorted()
                    .toList();
            int totalJavaFiles = allJavaFiles.size();
            List<Path> javaFiles = allJavaFiles.stream().limit(MAX_JAVA_FILES).toList();
            for (Path file : javaFiles) {
                String rel = scanRoot.relativize(file).toString().replace('\\', '/');
                try {
                    String source = Files.readString(file, StandardCharsets.UTF_8);
                    try (ParseHandle handle = sourceParser.parse("java", source)) {
                        var structure = structureExtractor.extract(handle);
                        typesByPath.put(rel, structure.types());
                        units.add(new CallGraph.SourceUnit(
                                rel,
                                structure.packageName(),
                                structure.types(),
                                structure.methods(),
                                dependencyExtractor.extractImports(handle),
                                dependencyExtractor.extractCallSites(handle)));
                        issues.addAll(auditEngine.audit(rel, handle, structure, null));
                    }
                } catch (Exception ignored) {
                    parseSkipped++;
                }
            }
            return new JavaScanResult(
                    issues, typesByPath, units, totalJavaFiles, javaFiles.size(), parseSkipped);
        } catch (IOException e) {
            throw BizException.of(ErrorCode.PARSE_ERROR, "扫描 Java 源码失败: " + e.getMessage(), e);
        }
    }

    private static String buildScanNote(JavaScanResult scan) {
        StringBuilder sb = new StringBuilder();
        sb.append("已扫描 ").append(scan.scannedJavaFiles).append(" / ").append(scan.totalJavaFiles).append(" 个 Java 文件");
        if (scan.totalJavaFiles > MAX_JAVA_FILES) {
            sb.append("（超过上限 ").append(MAX_JAVA_FILES).append("，仅分析前 ").append(MAX_JAVA_FILES).append(" 个，结果可能不完整）");
        }
        if (scan.parseSkipped > 0) {
            sb.append("；").append(scan.parseSkipped).append(" 个文件解析失败已跳过");
        }
        sb.append("。架构隐患与 Python 依赖冲突已纳入本次审计。");
        return sb.toString();
    }

    private Path scanRoot(long projectId) {
        Path workspace = Paths.get(workspaceProperties.getRoot()).resolve(String.valueOf(projectId));
        Path repoDir = workspace.resolve("repo");
        if (Files.isDirectory(repoDir)) {
            return repoDir;
        }
        return workspace;
    }

    private record JavaScanResult(
            List<AuditIssue> issues,
            Map<String, List<TypeDeclaration>> typesByPath,
            List<CallGraph.SourceUnit> units,
            int totalJavaFiles,
            int scannedJavaFiles,
            int parseSkipped
    ) {
    }
}

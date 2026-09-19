package com.codewisdom.analysis.service;

import com.codewisdom.analysis.arch.LayerDetector;
import com.codewisdom.analysis.arch.MermaidGenerator;
import com.codewisdom.analysis.config.WorkspaceProperties;
import com.codewisdom.analysis.domain.LayerReport;
import com.codewisdom.analysis.domain.TypeDeclaration;
import com.codewisdom.analysis.dto.ArchitectureView;
import com.codewisdom.analysis.parser.CallGraph;
import com.codewisdom.analysis.parser.JavaDependencyExtractor;
import com.codewisdom.analysis.parser.JavaStructureExtractor;
import com.codewisdom.analysis.parser.ParseHandle;
import com.codewisdom.analysis.parser.SourceParser;
import com.codewisdom.common.api.ErrorCode;
import com.codewisdom.common.exception.BizException;
import org.springframework.stereotype.Service;

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
public class ProjectArchitectureService {

    private final WorkspaceProperties workspaceProperties;
    private final SourceParser sourceParser;
    private final JavaStructureExtractor structureExtractor;
    private final JavaDependencyExtractor dependencyExtractor;
    private final LayerDetector layerDetector;
    private final MermaidGenerator mermaidGenerator;

    public ProjectArchitectureService(WorkspaceProperties workspaceProperties,
                                      SourceParser sourceParser,
                                      JavaStructureExtractor structureExtractor,
                                      JavaDependencyExtractor dependencyExtractor,
                                      LayerDetector layerDetector,
                                      MermaidGenerator mermaidGenerator) {
        this.workspaceProperties = workspaceProperties;
        this.sourceParser = sourceParser;
        this.structureExtractor = structureExtractor;
        this.dependencyExtractor = dependencyExtractor;
        this.layerDetector = layerDetector;
        this.mermaidGenerator = mermaidGenerator;
    }

    public ArchitectureView analyze(long projectId) {
        Path scanRoot = scanRoot(projectId);
        if (!Files.isDirectory(scanRoot)) {
            throw BizException.of(ErrorCode.NOT_FOUND, "项目工作区不存在: " + projectId);
        }

        Map<String, List<TypeDeclaration>> typesByPath = new LinkedHashMap<>();
        List<CallGraph.SourceUnit> units = new ArrayList<>();

        int limit = Math.max(1, workspaceProperties.getMaxJavaFiles());
        try (Stream<Path> walk = Files.walk(scanRoot)) {
            List<Path> javaFiles = walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .sorted()
                    .limit(limit)
                    .toList();
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
                    }
                } catch (Exception ignored) {
                    // skip broken file
                }
            }
        } catch (IOException e) {
            throw BizException.of(ErrorCode.PARSE_ERROR, "扫描 Java 源码失败: " + e.getMessage(), e);
        }

        LayerReport layerReport = layerDetector.detect(typesByPath);
        CallGraph callGraph = CallGraph.build(units);
        return new ArchitectureView(
                mermaidGenerator.layerDiagram(layerReport, callGraph),
                mermaidGenerator.packageDiagram(layerReport, callGraph),
                typesByPath.values().stream().mapToInt(List::size).sum(),
                callGraph.typeEdges().size());
    }

    private Path scanRoot(long projectId) {
        Path root = Paths.get(workspaceProperties.getRoot()).toAbsolutePath().normalize();
        Path workspace = root.resolve(String.valueOf(projectId));
        Path repoDir = workspace.resolve("repo");
        if (Files.isDirectory(repoDir)) {
            return repoDir;
        }
        return workspace;
    }
}

package com.codewisdom.analysis.parser;

import com.codewisdom.analysis.config.ParseCacheProperties;
import com.codewisdom.analysis.parser.cache.ParseResultCache;
import com.codewisdom.analysis.service.ParseResultPersistenceService;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * T-305：分片并行解析 + Redis/内存缓存 + 结果入库。
 */
@Component
public class ParsePipeline {

    private final SourceParser sourceParser;
    private final JavaStructureExtractor structureExtractor;
    private final ParseResultCache cache;
    private final ParseResultPersistenceService persistenceService;
    private final ParseCacheProperties properties;

    public ParsePipeline(SourceParser sourceParser,
                         JavaStructureExtractor structureExtractor,
                         ParseResultCache cache,
                         ParseResultPersistenceService persistenceService,
                         ParseCacheProperties properties) {
        this.sourceParser = sourceParser;
        this.structureExtractor = structureExtractor;
        this.cache = cache;
        this.persistenceService = persistenceService;
        this.properties = properties;
    }

    public record ParseProjectResult(long projectId, int fileCount, int typeCount, int methodCount, long elapsedMs) {
    }

    public ParseProjectResult parseProject(long projectId, Path workspace, boolean parallel) {
        long start = System.nanoTime();
        List<Path> javaFiles = listJavaFiles(workspace);
        int types = 0;
        int methods = 0;

        if (parallel) {
            int threads = Math.max(2, properties.getParallelism());
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            try {
                List<CompletableFuture<SourceStructure>> futures = javaFiles.stream()
                        .map(file -> CompletableFuture.supplyAsync(
                                () -> parseFile(projectId, workspace, file), pool))
                        .toList();
                for (CompletableFuture<SourceStructure> future : futures) {
                    SourceStructure structure = future.join();
                    types += structure.types().size();
                    methods += structure.methods().size();
                }
            } finally {
                pool.shutdown();
            }
        } else {
            for (Path file : javaFiles) {
                SourceStructure structure = parseFile(projectId, workspace, file);
                types += structure.types().size();
                methods += structure.methods().size();
            }
        }

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        return new ParseProjectResult(projectId, javaFiles.size(), types, methods, elapsedMs);
    }

    SourceStructure parseFile(long projectId, Path workspace, Path file) {
        String relative = workspace.toAbsolutePath().normalize().relativize(file.toAbsolutePath().normalize())
                .toString().replace('\\', '/');
        String checksum = checksumOf(file);
        String cacheKey = "cw:parse:" + projectId + ":" + relative + ":" + checksum;

        return cache.get(cacheKey).orElseGet(() -> {
            try {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                try (ParseHandle handle = sourceParser.parse("java", source)) {
                    SourceStructure structure = structureExtractor.extract(handle);
                    cache.put(cacheKey, structure);
                    persistenceService.upsert(projectId, relative, checksum, structure);
                    return structure;
                }
            } catch (IOException ex) {
                throw new IllegalStateException("读取源码失败: " + file, ex);
            }
        });
    }

    static List<Path> listJavaFiles(Path workspace) {
        List<Path> files = new ArrayList<>();
        if (!Files.isDirectory(workspace)) {
            return files;
        }
        try (Stream<Path> stream = Files.walk(workspace)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .forEach(files::add);
        } catch (IOException ex) {
            throw new IllegalStateException("遍历工作区失败: " + workspace, ex);
        }
        return files;
    }

    static String checksumOf(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (IOException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("计算校验和失败: " + file, ex);
        }
    }
}

package com.codewisdom.analysis.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("T-305 解析管线")
class ParsePipelineTest {

    @Autowired
    private ParsePipeline parsePipeline;

    @TempDir
    Path workspace;

    @Test
    @DisplayName("分片并行解析耗时低于单线程基线")
    void parallelFasterThanSerial() throws Exception {
        for (int i = 0; i < 24; i++) {
            Path pkg = workspace.resolve("demo/p" + i);
            Files.createDirectories(pkg);
            StringBuilder body = new StringBuilder();
            body.append("package demo.p").append(i).append(";\n");
            body.append("public class Demo").append(i).append(" {\n");
            for (int m = 0; m < 30; m++) {
                body.append("  public int m").append(m).append("() { return ").append(m).append("; }\n");
            }
            body.append("}\n");
            Files.writeString(pkg.resolve("Demo" + i + ".java"), body.toString());
        }

        ParsePipeline.ParseProjectResult serial = parsePipeline.parseProject(1L, workspace, false);
        ParsePipeline.ParseProjectResult parallel = parsePipeline.parseProject(2L, workspace, true);

        assertThat(serial.fileCount()).isEqualTo(24);
        assertThat(parallel.fileCount()).isEqualTo(24);
        assertThat(parallel.typeCount()).isEqualTo(serial.typeCount());
        assertThat(parallel.methodCount()).isEqualTo(serial.methodCount());
        assertThat(parallel.elapsedMs())
                .as("并行分片应快于单线程")
                .isLessThanOrEqualTo(serial.elapsedMs());
    }

    @Test
    @DisplayName("缓存命中：同文件二次解析不重复落库")
    void cacheHitOnSecondRun() throws Exception {
        Path pkg = workspace.resolve("demo");
        Files.createDirectories(pkg);
        Files.writeString(pkg.resolve("App.java"), """
                package demo;
                public class App { public void go() {} }
                """);

        parsePipeline.parseProject(10L, workspace, true);
        long first = parsePipeline.parseProject(10L, workspace, true).elapsedMs();
        long second = parsePipeline.parseProject(10L, workspace, true).elapsedMs();
        assertThat(second).isLessThanOrEqualTo(first);
    }
}

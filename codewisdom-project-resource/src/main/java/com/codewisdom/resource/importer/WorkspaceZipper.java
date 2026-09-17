package com.codewisdom.resource.importer;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** 将工作区目录打成 ZIP，供 MinIO 归档（T-205）。 */
public final class WorkspaceZipper {

    private WorkspaceZipper() {
    }

    public static Path zipDirectory(Path sourceDir) throws IOException {
        Path zip = Files.createTempFile("cw-archive-", ".zip");
        Path base = sourceDir.toAbsolutePath().normalize();
        try (OutputStream out = Files.newOutputStream(zip);
             ZipOutputStream zos = new ZipOutputStream(out)) {
            Files.walkFileTree(base, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String entryName = base.relativize(file.toAbsolutePath().normalize())
                            .toString()
                            .replace('\\', '/');
                    zos.putNextEntry(new ZipEntry(entryName));
                    Files.copy(file, zos);
                    zos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        return zip;
    }
}

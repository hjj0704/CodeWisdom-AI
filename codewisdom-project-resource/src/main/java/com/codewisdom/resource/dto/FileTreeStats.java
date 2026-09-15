package com.codewisdom.resource.dto;

import com.codewisdom.resource.domain.enums.FileCategory;

import java.util.Map;

/**
 * 文件树分类统计。
 *
 * <p>{@code totalNodes = directoryCount + fileCount}，三者同时给出是为了让前端
 * 不必自己算，也便于与 {@code t_project} 上的冗余计数字段对账。
 *
 * @param projectId      项目 id
 * @param totalNodes     节点总数（目录 + 文件）
 * @param directoryCount 目录数
 * @param fileCount      文件数
 * @param totalSize      文件总字节数（不含目录）
 * @param maxDepth       最大目录层级；只有根文件时为 0
 * @param byCategory     按分类计数
 * @param byLanguage     按语言计数；非源码文件不计入
 */
public record FileTreeStats(
        Long projectId,
        int totalNodes,
        int directoryCount,
        int fileCount,
        long totalSize,
        int maxDepth,
        Map<FileCategory, Integer> byCategory,
        Map<String, Integer> byLanguage
) {
}

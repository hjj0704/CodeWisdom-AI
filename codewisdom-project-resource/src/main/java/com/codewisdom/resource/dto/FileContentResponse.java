package com.codewisdom.resource.dto;

/**
 * 单文件内容（在线预览）。
 */
public record FileContentResponse(
        String path,
        String language,
        long size,
        boolean binary,
        String content
) {
}

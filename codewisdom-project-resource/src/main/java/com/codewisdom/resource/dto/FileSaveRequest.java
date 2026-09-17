package com.codewisdom.resource.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FileSaveRequest(
        @NotBlank(message = "path 不能为空")
        @Size(max = 1024)
        String path,

        @NotBlank(message = "content 不能为空")
        String content
) {
}

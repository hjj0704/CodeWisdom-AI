package com.codewisdom.resource.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 32, message = "用户名长度 3~32")
        @Pattern(regexp = "^[a-zA-Z0-9_\\-]+$", message = "用户名仅允许字母数字下划线")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 64, message = "密码长度 6~64")
        String password,

        @Size(max = 32, message = "昵称过长")
        String nickname
) {
}

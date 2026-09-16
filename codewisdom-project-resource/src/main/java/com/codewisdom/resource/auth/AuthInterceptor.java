package com.codewisdom.resource.auth;

import com.codewisdom.common.api.ErrorCode;
import com.codewisdom.common.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String ATTR_USER_ID = "cwAuthUserId";

    private final AuthTokenService tokenService;

    public AuthInterceptor(AuthTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw BizException.of(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        String token = header.substring("Bearer ".length()).trim();
        AuthTokenService.AuthUser user = tokenService.parseToken(token);
        if (user == null) {
            throw BizException.of(ErrorCode.UNAUTHORIZED, "登录已过期，请重新登录");
        }
        request.setAttribute(ATTR_USER_ID, user.userId());
        return true;
    }
}

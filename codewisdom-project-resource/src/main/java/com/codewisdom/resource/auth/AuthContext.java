package com.codewisdom.resource.auth;

import com.codewisdom.common.api.ErrorCode;
import com.codewisdom.common.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;

public final class AuthContext {

    private AuthContext() {
    }

    public static long requireUserId(HttpServletRequest request) {
        Object attr = request.getAttribute(AuthInterceptor.ATTR_USER_ID);
        if (attr instanceof Long userId) {
            return userId;
        }
        throw BizException.of(ErrorCode.UNAUTHORIZED, "请先登录");
    }

    /** 测试或关闭鉴权时使用；生产导入接口应走 {@link #requireUserId}。 */
    public static long resolveUserId(HttpServletRequest request) {
        Object attr = request.getAttribute(AuthInterceptor.ATTR_USER_ID);
        if (attr instanceof Long userId) {
            return userId;
        }
        return 0L;
    }
}

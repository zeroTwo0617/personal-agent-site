package me.zhengziheng.agent.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import me.zhengziheng.agent.common.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 管理接口鉴权拦截器：拦截 /api/admin/**，校验 JWT 且用户名 == 站长账号。
 * 未带 token / 校验失败 / 非站长 → 401。配合 SecurityConfig 的 CORS 白名单与公开路径规则。
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final String adminUsername;

    public AdminAuthInterceptor(JwtUtil jwtUtil,
                                @Value("${security.admin.username:admin}") String adminUsername) {
        this.jwtUtil = jwtUtil;
        this.adminUsername = adminUsername;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            try {
                String username = jwtUtil.parseUsername(auth.substring(7));
                if (adminUsername.equals(username)) {
                    return true;
                }
            } catch (Exception ignore) {
                // 落空
            }
        }
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"未登录或无权限\",\"data\":null}");
        return false;
    }
}

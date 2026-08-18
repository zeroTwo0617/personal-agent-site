package me.zhengziheng.agent.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import me.zhengziheng.agent.service.RateLimitService;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 每 IP 限流拦截器：/api/chat、/api/feedback 按问答档，/api/auth/login 按登录档。
 * 超限返回 429 + 友好提示；真实 IP 从 X-Real-IP（Nginx 注入）取，回退 X-Forwarded-For / remoteAddr。
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitService rateLimitService;

    public RateLimitInterceptor(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String ip = resolveIp(request);
        String uri = request.getRequestURI();
        boolean allowed = uri.startsWith("/api/auth/login")
                ? rateLimitService.tryAcquireLogin(ip)
                : rateLimitService.tryAcquireQuestion(ip);
        if (!allowed) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":429,\"message\":\"提问太频繁啦，请稍后再试\",\"data\":null}");
            return false;
        }
        return true;
    }

    private String resolveIp(HttpServletRequest request) {
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.split(",")[0].trim();
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

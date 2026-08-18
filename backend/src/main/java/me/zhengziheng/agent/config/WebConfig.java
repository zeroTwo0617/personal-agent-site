package me.zhengziheng.agent.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：注册限流与鉴权拦截器。
 *  - 限流：/api/chat（问答提交）、/api/feedback（反馈）、/api/auth/login（登录，更严档）
 *  - 鉴权：/api/admin/**（站长 JWT）
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;
    private final AdminAuthInterceptor adminAuthInterceptor;

    public WebConfig(RateLimitInterceptor rateLimitInterceptor,
                     AdminAuthInterceptor adminAuthInterceptor) {
        this.rateLimitInterceptor = rateLimitInterceptor;
        this.adminAuthInterceptor = adminAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/chat", "/api/feedback", "/api/auth/login");
        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/api/admin/**", "/api/documents/**", "/api/eval/**");
    }
}

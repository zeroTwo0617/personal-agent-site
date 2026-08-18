package me.zhengziheng.agent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * 安全配置：CORS 白名单（app.cors-origins 配置，裸域 + www + 本地开发）+ 关闭 CSRF。
 * 鉴权由 WebConfig 注册的 AdminAuthInterceptor 负责（/api/admin/**、/api/documents/**、/api/eval/** 需站长 JWT），
 * 限流由 RateLimitInterceptor 负责；Spring Security 层保持 permitAll（访客匿名访问 /api/chat、/api/feedback、/api/auth/login）。
 */
@Configuration
public class SecurityConfig {

    @Value("${app.cors-origins:https://zhengziheng.me,https://www.zhengziheng.me,http://localhost:5173}")
    private String corsOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration c = new CorsConfiguration();
        List<String> origins = Arrays.stream(corsOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        c.setAllowedOriginPatterns(origins);
        // 含 DELETE：文档删除接口是跨域 DELETE，不在白名单里浏览器会拦截预检请求（表现为"按钮点了没反应"）
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        c.setAllowedHeaders(List.of("*"));
        c.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", c);
        return src;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}

package me.zhengziheng.agent.service;

import me.zhengziheng.agent.common.BusinessException;
import me.zhengziheng.agent.common.ErrorCode;
import me.zhengziheng.agent.common.JwtUtil;
import me.zhengziheng.agent.dto.response.LoginResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 鉴权服务：单站长账号（配置注入，不落库，无开放注册）。
 * - security.admin.username / security.admin.password 来自配置（env）；
 * - 密码支持 BCrypt 哈希（以 $2 开头）或明文（本地开发兜底，生产务必用哈希）；
 * - 登录成功签发 JWT，供管理接口（/api/admin/**）鉴权。
 */
@Service
public class AuthService {

    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private final String adminUsername;
    private final String adminPassword;

    public AuthService(JwtUtil jwtUtil,
                       @Value("${security.admin.username:admin}") String adminUsername,
                       @Value("${security.admin.password:}") String adminPassword) {
        this.jwtUtil = jwtUtil;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    public LoginResponse login(String username, String password) {
        if (adminPassword == null || adminPassword.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "站长账号未配置（ADMIN_PASSWORD 为空）");
        }
        boolean ok;
        if (adminPassword.startsWith("$2")) {
            ok = encoder.matches(password, adminPassword);
        } else {
            // 明文兜底：仅本地开发；生产必须改用 BCrypt 哈希
            ok = adminPassword.equals(password);
        }
        if (!adminUsername.equals(username) || !ok) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        String token = jwtUtil.generateToken(username);
        LoginResponse resp = new LoginResponse();
        resp.setToken(token);
        resp.setUsername(username);
        resp.setExpiresIn(86400L);
        return resp;
    }
}

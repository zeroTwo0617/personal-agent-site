package me.zhengziheng.agent.dto.response;

import lombok.Data;

/**
 * 登录返回：携带 JWT，前端后续请求须在 Authorization 头带上 "Bearer <token>"。
 */
@Data
public class LoginResponse {

    /** JWT 字符串 */
    private String token;
    /** 登录用户名 */
    private String username;
    /** 过期秒数（默认 86400=1 天） */
    private Long expiresIn;
}

package me.zhengziheng.agent.dto.response;

import lombok.Data;

/**
 * 注册成功返回的用户视图（不含密码等敏感信息）。
 */
@Data
public class AuthUserVO {

    /** 自增用户 ID（M1 为内存计数器） */
    private Long userId;
    /** 用户名 */
    private String username;
}

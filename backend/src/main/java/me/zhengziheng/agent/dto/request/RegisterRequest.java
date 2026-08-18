package me.zhengziheng.agent.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求体：POST /api/auth/register（M1 为内存用户，仅演示流程）
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 32, message = "用户名长度 3~32")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
}

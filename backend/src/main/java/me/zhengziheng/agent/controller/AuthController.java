package me.zhengziheng.agent.controller;

import me.zhengziheng.agent.common.Result;
import me.zhengziheng.agent.dto.request.LoginRequest;
import me.zhengziheng.agent.dto.response.LoginResponse;
import me.zhengziheng.agent.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "鉴权")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "站长登录（返回 JWT；登录接口挂更严限流，见 WebConfig）")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        return Result.success(authService.login(req.getUsername(), req.getPassword()));
    }
}

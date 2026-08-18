package me.zhengziheng.agent.common;

import lombok.Getter;

/**
 * 业务错误码，与 api-contract.md 保持一致。
 */
@Getter
public enum ErrorCode {

    PARAM_ERROR(400, "参数错误"),          // 入参校验失败（如文件非 .md、问题为空）
    UNAUTHORIZED(401, "未登录或无权限"),    // 鉴权失败（后续里程碑启用 JWT 过滤器后生效）
    NOT_FOUND(404, "资源不存在"),          // 文档/分块等按 ID 查不到
    CONFLICT(409, "资源冲突"),             // 用户名重复等唯一性冲突
    INTERNAL_ERROR(500, "服务器内部错误");  // 未预期的异常（如读文件失败）

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}

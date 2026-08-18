package me.zhengziheng.agent.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应包络：所有 HTTP 接口都返回 { code, message, data }。
 * 约定：code=0 表示成功，非 0 表示业务/系统错误（见 ErrorCode 枚举）。
 */
@Data
public class Result<T> implements Serializable {

    /** 状态码：0=成功；其他值见 ErrorCode（400/401/404/409/500） */
    private int code;
    /** 提示信息：成功为 "success"，失败为错误描述 */
    private String message;
    /** 业务数据：成功时承载结果，失败时通常为 null */
    private T data;

    public static <T> Result<T> success(T data) {
        Result<T> r = new Result<>();
        r.setCode(0);
        r.setMessage("success");
        r.setData(data);
        return r;
    }

    public static Result<Void> success() {
        return success(null);
    }

    public static <T> Result<T> error(int code, String message) {
        Result<T> r = new Result<>();
        r.setCode(code);
        r.setMessage(message);
        r.setData(null);
        return r;
    }

    public static <T> Result<T> error(ErrorCode errorCode) {
        return error(errorCode.getCode(), errorCode.getMessage());
    }
}

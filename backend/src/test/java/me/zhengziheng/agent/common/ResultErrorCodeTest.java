package me.zhengziheng.agent.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 统一响应包络 + 错误码单测：与 api-contract.md 的约定锁死。
 */
class ResultErrorCodeTest {

    @Test
    void success_carriesData() {
        Result<String> r = Result.success("hello");
        assertEquals(0, r.getCode());
        assertEquals("success", r.getMessage());
        assertEquals("hello", r.getData());
    }

    @Test
    void successNoData_dataIsNull() {
        Result<Void> r = Result.success();
        assertEquals(0, r.getCode());
        assertNull(r.getData());
    }

    @Test
    void error_codeAndMessage() {
        Result<Void> r = Result.error(404, "资源不存在");
        assertEquals(404, r.getCode());
        assertEquals("资源不存在", r.getMessage());
        assertNull(r.getData());
    }

    @Test
    void errorFromEnum_matchesContract() {
        Result<Void> r = Result.error(ErrorCode.NOT_FOUND);
        assertEquals(404, r.getCode());
        assertEquals("资源不存在", r.getMessage());
    }

    @Test
    void errorCodes_matchApiContract() {
        assertEquals(400, ErrorCode.PARAM_ERROR.getCode());
        assertEquals(401, ErrorCode.UNAUTHORIZED.getCode());
        assertEquals(404, ErrorCode.NOT_FOUND.getCode());
        assertEquals(409, ErrorCode.CONFLICT.getCode());
        assertEquals(500, ErrorCode.INTERNAL_ERROR.getCode());
    }
}

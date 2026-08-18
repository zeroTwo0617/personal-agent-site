package me.zhengziheng.agent.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JwtUtil 单测：签发 / 解析 / 校验 / 过期 / 篡改。
 * secret 与 expiration 通过反射注入（字段来自 @Value 配置）。
 */
class JwtUtilTest {

    private JwtUtil util;

    /** HS256 要求密钥 >= 32 字节 */
    private static final String SECRET = "unit-test-secret-key-0123456789abcdef0123456789abcdef";
    private static final long EXPIRATION = 86400L;

    @BeforeEach
    void setUp() {
        util = new JwtUtil();
        ReflectionTestUtils.setField(util, "secret", SECRET);
        ReflectionTestUtils.setField(util, "expiration", EXPIRATION);
    }

    @Test
    void generateAndParse_roundTrip() {
        String token = util.generateToken("alice");
        assertEquals("alice", util.parseUsername(token));
        assertTrue(util.validate(token));
    }

    @Test
    void tamperedToken_invalid() {
        String token = util.generateToken("alice");
        // 注意：jjwt 0.12.x 对"签名段尾部追加字符"是宽容的（仍能解析通过），
        // 因此这里用"翻转签名末字符"来构造必然失败的篡改（实测抛 SignatureException）
        String tampered = token.substring(0, token.length() - 1)
                + (token.endsWith("a") ? "b" : "a");
        assertFalse(util.validate(tampered));
    }

    @Test
    void tamperedPayload_invalid() {
        String token = util.generateToken("alice");
        // 篡改 payload 段：替换 payload 首字符（避开原字符，保证确实不同）
        int firstDot = token.indexOf('.');
        char orig = token.charAt(firstDot + 1);
        char replacement = (orig == 'x') ? 'y' : 'x';
        String tampered = token.substring(0, firstDot + 1) + replacement + token.substring(firstDot + 2);
        assertFalse(util.validate(tampered));
    }

    @Test
    void expiredToken_invalid() {
        ReflectionTestUtils.setField(util, "expiration", -1L); // 过期时间在过去
        String token = util.generateToken("alice");
        assertFalse(util.validate(token));
    }

    @Test
    void differentSecret_cannotValidate() {
        String token = util.generateToken("alice");
        JwtUtil other = new JwtUtil();
        ReflectionTestUtils.setField(other, "secret", "another-secret-key-0123456789abcdef0123456789");
        ReflectionTestUtils.setField(other, "expiration", EXPIRATION);
        assertFalse(other.validate(token));
    }

    @Test
    void garbageToken_invalid() {
        assertFalse(util.validate("not-a-jwt"));
        assertFalse(util.validate(""));
    }
}

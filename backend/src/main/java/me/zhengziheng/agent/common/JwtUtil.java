package me.zhengziheng.agent.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具：签发与解析。M1 仅提供登录签发能力，过滤器在后续里程碑接入。
 */
@Component
public class JwtUtil {

    /** JWT 签名密钥（来自配置 jwt.secret，生产环境务必改掉默认值，否则可被伪造） */
    @Value("${jwt.secret}")
    private String secret;

    /** 有效期（秒），默认 86400=1 天 */
    @Value("${jwt.expiration:86400}")
    private Long expiration;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String username) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + expiration * 1000);
        return Jwts.builder()
                .subject(username)
                .issuedAt(now)
                .expiration(exp)
                .signWith(key())
                .compact();
    }

    public String parseUsername(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }

    public boolean validate(String token) {
        try {
            parseUsername(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

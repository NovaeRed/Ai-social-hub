import cn.redture.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
@SpringBootTest(classes = JwtUtil.class)
public class JwtTokenTest {

    @Autowired
    private JwtUtil jwtUtil;

    @Test
    public void testCreateAndParseToken() {
        // 1. 准备声明
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", 1);
        claims.put("mobile", "13800138000");

        // 2. 使用 JwtUtil 生成令牌
        String token = jwtUtil.generateAccessToken(claims);

        // 3. 使用 JwtUtil 解析令牌
        Claims parsedClaims = jwtUtil.getClaimsFromToken(token);

        // 4. 验证声明内容
        log.debug(parsedClaims.toString());
        assertEquals(1, parsedClaims.get("id", Integer.class));
        assertEquals("13800138000", parsedClaims.get("mobile", String.class));
    }
}

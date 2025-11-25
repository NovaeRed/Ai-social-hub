package cn.redture.common.util;

import cn.redture.common.dto.UserPrincipal;
import cn.redture.common.exception.BaseException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 用于访问 Spring Security 上下文的工具类。
 */
public class SecurityContextHolderUtil {

    /**
     * 获取当前已认证用户的用户ID
     *
     * @return 用户ID (Long类型)
     * @throws BaseException 如果用户未认证或认证信息无效
     */
    public static Long getUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new BaseException(HttpStatus.UNAUTHORIZED, "用户未认证，请先登录");
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof UserPrincipal) {
            String uid = ((UserPrincipal) principal).getUid();
            return Long.valueOf(uid);
        } else if (principal instanceof String && "anonymousUser".equals(principal)) {
            // 处理匿名用户情况
            throw new BaseException(HttpStatus.UNAUTHORIZED, "用户未认证，请先登录");
        }

        // 处理未知 principal 类型的回退或错误
        throw new BaseException(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误：无法解析认证信息");
    }
}

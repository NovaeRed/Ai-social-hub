package cn.redture.identity.service;

import cn.redture.common.exception.businessException.AccessDeniedException;
import cn.redture.common.exception.businessException.InvalidInputException;
import cn.redture.common.exception.businessException.RateLimitException;
import cn.redture.common.exception.businessException.ResourceNotFoundException;
import cn.redture.identity.mapper.UserMapper;
import cn.redture.identity.pojo.entity.User;
import cn.redture.identity.pojo.vo.UserLookupResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Locale;

@Service
public class UserLookupService {
    private static final DefaultRedisScript<Long> LIMIT = new DefaultRedisScript<>(
            "local n = redis.call('INCR', KEYS[1]); if n == 1 then redis.call('EXPIRE', KEYS[1], 60) end; return n", Long.class);
    @Resource private UserMapper userMapper;
    @Resource private StringRedisTemplate redis;

    public UserLookupResult lookup(Long currentUserId, String type, String value) {
        if (currentUserId == null) throw new AccessDeniedException("请先登录");
        String normalizedType = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        String normalizedValue = value == null ? "" : value.trim();
        if (!List.of("USERNAME", "PHONE", "PUBLIC_ID").contains(normalizedType))
            throw new InvalidInputException("查找类型必须为 USERNAME、PHONE 或 PUBLIC_ID");
        if (normalizedValue.isEmpty() || normalizedValue.length() > 100)
            throw new InvalidInputException("请提供有效的精确查找内容");
        if (normalizedType.equals("PHONE") && !normalizedValue.matches("1[3-9]\\d{9}"))
            throw new InvalidInputException("请输入有效的中国大陆手机号");
        if (normalizedType.equals("PUBLIC_ID") && !normalizedValue.matches("[0-9A-HJKMNP-TV-Z]{26}"))
            throw new InvalidInputException("用户 ID 格式不正确");

        Long count = redis.execute(LIMIT, List.of("user:lookup:rate:" + currentUserId));
        if (count == null || count > 10) throw new RateLimitException("查找过于频繁，请一分钟后重试");
        LambdaQueryWrapper<User> query = new LambdaQueryWrapper<User>()
                .select(User::getPublicId, User::getUsername, User::getNickname, User::getAvatarUrl)
                .isNull(User::getDeletedAt);
        switch (normalizedType) {
            case "PHONE" -> query.eq(User::getPhone, normalizedValue);
            case "USERNAME" -> query.eq(User::getUsername, normalizedValue);
            case "PUBLIC_ID" -> query.eq(User::getPublicId, normalizedValue);
            default -> throw new InvalidInputException("查找类型不正确");
        }
        User user = userMapper.selectOne(query);
        if (user == null) throw new ResourceNotFoundException("没有找到可添加的用户");
        String nickname = user.getNickname();
        if (nickname == null || nickname.isBlank() || nickname.startsWith("AIHub_")) nickname = user.getUsername();
        return new UserLookupResult(user.getPublicId(), user.getUsername(), nickname, user.getAvatarUrl());
    }
}

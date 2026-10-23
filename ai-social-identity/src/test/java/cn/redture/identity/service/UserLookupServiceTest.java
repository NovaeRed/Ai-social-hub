package cn.redture.identity.service;

import cn.redture.common.exception.businessException.*;
import cn.redture.identity.mapper.UserMapper;
import cn.redture.identity.pojo.entity.User;
import cn.redture.identity.pojo.vo.UserLookupResult;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserLookupServiceTest {
    UserLookupService service; UserMapper users; StringRedisTemplate redis;
    @BeforeAll static void metadata() { TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "lookup-test"), User.class); }
    @BeforeEach void setup() {
        service = new UserLookupService(); users = mock(UserMapper.class); redis = mock(StringRedisTemplate.class);
        ReflectionTestUtils.setField(service, "userMapper", users); ReflectionTestUtils.setField(service, "redis", redis);
        when(redis.execute(any(RedisScript.class), anyList())).thenReturn(1L);
    }
    @Test void exactLookupReturnsOnlyConfirmationInformation() throws Exception {
        User user = new User(); user.setPublicId("public"); user.setUsername("linmu"); user.setNickname("林沐"); user.setPhone("13800138000"); user.setEmail("private@example.test"); user.setPasswordHash("hash");
        when(users.selectOne(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<User> query = invocation.getArgument(0);
            assertTrue(query.getSqlSegment().contains("username ="));
            assertTrue(query.getSqlSegment().contains("deleted_at IS NULL"));
            assertFalse(query.getSqlSelect().contains("phone")); assertFalse(query.getSqlSelect().contains("password"));
            return user;
        });
        UserLookupResult result = service.lookup(9L, "USERNAME", " linmu ");
        String json = new ObjectMapper().writeValueAsString(result);
        assertTrue(json.contains("林沐")); assertTrue(json.contains("public_id"));
        assertFalse(json.contains("phone")); assertFalse(json.contains("email")); assertFalse(json.contains("hash"));
    }
    @Test void invalidTypesAndPhonesNeverQueryUsersOrRedis() {
        assertThrows(InvalidInputException.class, () -> service.lookup(9L, "EMAIL", "x"));
        assertThrows(InvalidInputException.class, () -> service.lookup(9L, "PHONE", "123"));
        verifyNoInteractions(users); verify(redis, never()).execute(any(RedisScript.class), anyList());
    }
    @Test void anonymousAndOverLimitRequestsNeverQueryUsers() {
        assertThrows(AccessDeniedException.class, () -> service.lookup(null, "USERNAME", "linmu"));
        when(redis.execute(any(RedisScript.class), anyList())).thenReturn(11L);
        assertThrows(RateLimitException.class, () -> service.lookup(9L, "USERNAME", "linmu"));
        verifyNoInteractions(users);
    }
    @Test void missingUsersProduceOneGenericNotFoundResponse() {
        assertThrows(ResourceNotFoundException.class, () -> service.lookup(9L, "PHONE", "13800138000"));
    }
    @Test void oldGeneratedNamesUseTheChosenUsername() {
        User user = new User(); user.setPublicId("public"); user.setUsername("linmu"); user.setNickname("AIHub_generated");
        when(users.selectOne(any())).thenReturn(user);
        assertEquals("linmu", service.lookup(9L, "USERNAME", "linmu").nickname());
    }
}

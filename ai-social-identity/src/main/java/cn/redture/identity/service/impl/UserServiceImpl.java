package cn.redture.identity.service.impl;

import cn.redture.aiEngine.service.AiConfigService;
import cn.redture.common.pojo.dto.UserPrincipal;
import cn.redture.common.exception.businessException.InvalidInputException;
import cn.redture.common.exception.businessException.ResourceNotFoundException;
import cn.redture.common.util.RegexUtil;
import cn.redture.common.util.SecurityContextHolderUtil;
import cn.redture.identity.pojo.dto.UpdateUserDTO;
import cn.redture.identity.pojo.entity.User;
import cn.redture.identity.mapper.UserMapper;
import cn.redture.identity.pojo.vo.UserInformation;
import cn.redture.identity.service.UserService;
import cn.redture.identity.util.TokenManagementUtil;
import cn.redture.identity.util.converter.UserConverter;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

@Slf4j
@Service
public class UserServiceImpl implements UserService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Resource
    private TokenManagementUtil tokenManagementUtil;

    @Resource
    private AiConfigService aiConfigService;

    @Override
    public UserInformation getUserById(Long userId) {
        if (userId == null) {
            throw new ResourceNotFoundException("用户ID");
        }

        try {
            User user = Optional.ofNullable(userMapper.selectById(userId))
                    .orElseThrow(() -> new ResourceNotFoundException("用户"));
            log.debug("找到用户: {}", user);
            return UserConverter.INSTANCE.toUserInformation(user);
        } catch (NumberFormatException e) {
            throw new InvalidInputException("无效的用户ID格式");
        }
    }

    @Override
    public UserInformation updateUserInfo(UpdateUserDTO updateUserDTO) {
        Long userId = SecurityContextHolderUtil.getUserId();
        User user = Optional.ofNullable(userMapper.selectById(userId))
                .orElseThrow(() -> new ResourceNotFoundException("用户"));

        // 记录 AI 授权旧值（null 视为 false）
        Boolean oldAiEnabled = user.getAiAnalysisEnabled();
        if (oldAiEnabled == null) {
            oldAiEnabled = Boolean.FALSE;
        }

        // 如果提供了 email，则校验格式
        if (StringUtils.hasText(updateUserDTO.getEmail()) && !RegexUtil.isEmail(updateUserDTO.getEmail())) {
            throw new InvalidInputException("邮箱格式不正确");
        }

        // 如果提供了 phone，则校验格式
        if (StringUtils.hasText(updateUserDTO.getPhone()) && !RegexUtil.isPhone(updateUserDTO.getPhone())) {
            throw new InvalidInputException("手机号码格式不正确");
        }

        UserConverter.INSTANCE.updateUserFromDto(updateUserDTO, user);

        userMapper.updateById(user);
        log.info("用户 {} 更新了个人信息", userId);

        // 如果 AI 授权状态发生变化，通知 AI 引擎模块
        Boolean newAiEnabled = user.getAiAnalysisEnabled();
        if (newAiEnabled == null) {
            newAiEnabled = Boolean.FALSE;
        }
        if (!oldAiEnabled.equals(newAiEnabled)) {
            aiConfigService.onAiAnalysisToggled(userId, newAiEnabled);
        }

        return UserConverter.INSTANCE.toUserInformation(user);
    }

    @Override
    public void changePassword(Long userId, String currentPassword, String newPassword) {

        if (userId == null) {
            throw new ResourceNotFoundException("用户ID");
        }

        if (!userId.equals(SecurityContextHolderUtil.getUserId())) {
            throw new InvalidInputException("只能修改当前登录用户的密码");
        }

        if (!RegexUtil.isStrongPassword(newPassword)) {
            throw new InvalidInputException("新密码不符合强密码要求，密码至少8位，且包含大小写字母、数字和特殊字符");
        }

        User user = Optional.ofNullable(userMapper.selectById(userId))
                .orElseThrow(() -> new ResourceNotFoundException("用户"));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new InvalidInputException("旧密码不正确");
        }

        String newHashedPassword = passwordEncoder.encode(newPassword);
        LambdaUpdateWrapper<User> updateWrapper = new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getPasswordHash, newHashedPassword);
        userMapper.update(null, updateWrapper);
        log.debug("用户 {} 修改了密码", userId);

        // 将当前请求的 Access Token 加入黑名单，防止继续使用
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null) {
                Object principal = authentication.getPrincipal();
                if (principal instanceof UserPrincipal) {
                    String currentAccessToken = ((UserPrincipal) principal).getAccessToken();
                    tokenManagementUtil.addAccessTokenToBlacklist(currentAccessToken);
                }
            }
        } catch (Exception e) {
            log.warn("在将当前 access token 加入黑名单时发生异常：{}", e.getMessage());
        }

        tokenManagementUtil.deleteRefreshToken(userId);
        try {
            SecurityContextHolder.clearContext();
        } catch (Exception ignored) {
        }

        log.debug("用户 {} 修改密码后，已签发的令牌已加入黑名单", userId);

    }

    @Override
    public Long resolveUserIdByPublicId(String publicId) {
        if (!StringUtils.hasText(publicId)) {
            return null;
        }

        User user = userMapper.selectByPublicId(publicId);
        return user != null ? user.getId() : null;
    }

    @Override
    public List<Long> getUserIdsByPublicIds(List<String> publicIds) {
        if (publicIds == null || publicIds.isEmpty()) {
            return Collections.emptyList();
        }
        return userMapper.selectIdsByPublicIds(publicIds);
    }

    @Override
    public boolean isAiAnalysisEnabled(Long userId) {
        User user = userMapper.selectById(userId);
        return user != null && Boolean.TRUE.equals(user.getAiAnalysisEnabled());
    }
}

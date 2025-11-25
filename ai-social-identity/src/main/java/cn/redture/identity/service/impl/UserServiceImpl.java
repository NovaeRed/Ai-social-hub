package cn.redture.identity.service.impl;

import cn.redture.common.exception.BusinessException.InvalidInputException;
import cn.redture.common.exception.BusinessException.ResourceNotFoundException;
import cn.redture.common.util.RegexUtil;
import cn.redture.common.util.SecurityContextHolderUtil;
import cn.redture.identity.pojo.dto.UpdateUserDTO;
import cn.redture.identity.pojo.entity.User;
import cn.redture.identity.mapper.UserMapper;
import cn.redture.identity.pojo.vo.UserInformation;
import cn.redture.identity.service.UserService;
import cn.redture.identity.util.converter.UserConverter;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Slf4j
@Service
public class UserServiceImpl implements UserService {

    @Resource
    private UserMapper userMapper;

    @Override
    public UserInformation getUserById(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new ResourceNotFoundException("用户", "用户ID不能为空");
        }

        try {
            Long id = Long.valueOf(userId);
            User user = Optional.ofNullable(userMapper.selectById(id))
                    .orElseThrow(() -> new ResourceNotFoundException("用户"));
            log.debug("找到用户: {}", user);
            return UserConverter.INSTANCE.toUserInformation(user);
        } catch (NumberFormatException e) {
            throw new ResourceNotFoundException("用户", "无效的用户ID格式");
        }
    }

    @Override
    public UserInformation updateUserInfo(UpdateUserDTO updateUserDTO) {
        Long userId = SecurityContextHolderUtil.getUserId();
        User user = Optional.ofNullable(userMapper.selectById(userId))
                .orElseThrow(() -> new ResourceNotFoundException("用户"));

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

        return UserConverter.INSTANCE.toUserInformation(user);
    }
}

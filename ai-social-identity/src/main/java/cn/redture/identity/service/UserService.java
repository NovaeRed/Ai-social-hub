package cn.redture.identity.service;

import cn.redture.identity.pojo.dto.UpdateUserDTO;
import cn.redture.identity.pojo.entity.User;
import cn.redture.identity.pojo.vo.UserInformation;

public interface UserService {
    UserInformation getUserById(String userId);

    /**
     * 更新当前登录用户的信息
     * @param updateUserDTO 包含要更新字段的 DTO
     * @return 更新后的用户信息
     */
    UserInformation updateUserInfo(UpdateUserDTO updateUserDTO);
}

package cn.redture.identity.service;

import cn.redture.identity.pojo.dto.UpdateUserDTO;
import cn.redture.identity.pojo.entity.User;
import cn.redture.identity.pojo.vo.UserInformation;

import java.util.List;
import java.util.Map;

public interface UserService {

    /**
     * 根据用户主键 ID 获取用户信息
     *
     * @param userId 用户主键 ID
     * @return 用户信息
     */
    UserInformation getUserById(Long userId);

    /**
     * 更新当前登录用户的信息
     *
     * @param updateUserDTO 包含要更新字段的 DTO
     * @return 更新后的用户信息
     */
    UserInformation updateUserInfo(UpdateUserDTO updateUserDTO);

    /**
     * 修改用户密码
     *
     * @param userId          用户主键 ID
     * @param currentPassword 当前密码
     * @param newPassword     新密码
     */
    void changePassword(Long userId, String currentPassword, String newPassword);

    /**
     * 根据对外 public_id 解析内部用户主键 ID。
     *
     * @param publicId 用户对外 public_id
     * @return 内部用户 ID，若不存在则返回 null
     */
    Long resolveUserIdByPublicId(String publicId);

    /**
     * 批量根据对外 public_id 解析内部用户主键 ID。
     *
     * @param publicIds 用户对外 public_id 列表
     * @return 对应的内部用户 ID 列表，无序
     */
    List<Long> getUserIdsByPublicIds(List<String> publicIds);
}

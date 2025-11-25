package cn.redture.identity.pojo.dto;

import lombok.Data;

/**
 * 用于更新用户信息的 DTO
 */
@Data
public class UpdateUserDTO {

    private String nickname;

    private String avatarUrl;

    private String email;

    private String phone;

    // TODO 对该字段产生的影响进行评估
    private Boolean aiAnalysisEnabled;
}

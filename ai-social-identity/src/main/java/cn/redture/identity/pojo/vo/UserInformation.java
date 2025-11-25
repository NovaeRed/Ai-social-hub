package cn.redture.identity.pojo.vo;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class UserInformation {

    private String publicId;
    private String username;
    private String nickname;
    private String avatarUrl;
    private String email;
    private String phone;
    private String vipLevel;
    private Boolean aiAnalysisEnabled;

}

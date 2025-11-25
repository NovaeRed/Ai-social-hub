package cn.redture.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPrincipal {

    private String uid;
    private String username;
    private String accessToken;

    public UserPrincipal(String uid, String username) {
        this.uid = uid;
        this.username = username;
    }
}

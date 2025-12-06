package cn.redture.chat.pojo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class AddMembersRequestDTO {
    @JsonProperty("user_public_ids")
    private List<String> userPublicIds;
}


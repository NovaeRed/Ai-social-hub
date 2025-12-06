package cn.redture.chat.pojo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class NewOwnerRequestDTO {

    @JsonProperty("new_owner_public_id")
    private String newOwnerPublicId;
}

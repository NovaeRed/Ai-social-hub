package cn.redture.chat.pojo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Data
public class CreateGroupRequestDTO {
    private String name;
    @JsonProperty("member_public_ids")
    private List<String> memberPublicIds;

}


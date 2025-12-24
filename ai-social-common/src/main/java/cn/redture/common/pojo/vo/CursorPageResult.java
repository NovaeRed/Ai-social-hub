package cn.redture.common.pojo.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class CursorPageResult<T> {

    private List<T> items;

    @JsonProperty("next_cursor")
    private Long nextCursor;

    @JsonProperty("has_more")
    private boolean hasMore;
}

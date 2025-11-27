package cn.redture.identity.pojo.vo;

import lombok.Data;

import java.util.List;

/**
 * 好友请求列表响应，包含 incoming / outgoing。
 */
@Data
public class FriendRequestListVO {

    private List<FriendRequestItemVO> incoming;

    private List<FriendRequestItemVO> outgoing;
}

package cn.redture.identity.service;

import cn.redture.identity.mapper.UserMapper;
import cn.redture.identity.mapper.FriendshipMapper;
import cn.redture.identity.mapper.FriendshipRequestMapper;
import cn.redture.identity.pojo.entity.User;
import cn.redture.identity.pojo.entity.FriendshipRequest;
import cn.redture.identity.pojo.enums.FriendshipRequestStatus;
import cn.redture.identity.pojo.dto.FriendRequestCreateDTO;
import cn.redture.identity.service.impl.FriendshipServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class FriendReapplicationTest {
    @Test void removedFriendsCanReapplyWithoutDuplicatingHistoricalRequests() {
        FriendshipServiceImpl service = new FriendshipServiceImpl();
        UserMapper users = mock(UserMapper.class);
        FriendshipMapper friendships = mock(FriendshipMapper.class);
        FriendshipRequestMapper requests = mock(FriendshipRequestMapper.class);
        ReflectionTestUtils.setField(service,"userMapper", users);
        ReflectionTestUtils.setField(service,"friendshipMapper", friendships);
        ReflectionTestUtils.setField(service,"friendshipRequestMapper", requests);
        User current = new User(); current.setId(1L);
        User target = new User(); target.setId(2L);
        when(users.selectById(1L)).thenReturn(current);
        when(users.selectOne(any())).thenReturn(target);
        when(friendships.selectCount(any())).thenReturn(0L);
        FriendshipRequest old = new FriendshipRequest(); old.setId(3L); old.setStatus(FriendshipRequestStatus.ACCEPTED);
        when(requests.selectOne(any())).thenReturn(old);
        FriendRequestCreateDTO dto = new FriendRequestCreateDTO(); dto.setTargetUserPublicId("peer"); dto.setMessage("重新添加");
        service.sendFriendRequest(1L,dto);
        assertEquals(FriendshipRequestStatus.PENDING,old.getStatus());
        assertEquals("重新添加",old.getMessage());
        verify(requests).updateById(old); verify(requests,never()).insert(any(FriendshipRequest.class));
    }
}

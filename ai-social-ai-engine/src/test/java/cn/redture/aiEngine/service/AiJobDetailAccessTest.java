package cn.redture.aiEngine.service;

import cn.redture.aiEngine.controller.AiJobController;
import cn.redture.aiEngine.mapper.AiTaskMapper;
import cn.redture.aiEngine.pojo.entity.AiTask;
import cn.redture.aiEngine.pojo.enums.AiTaskStatus;
import cn.redture.aiEngine.pojo.enums.AiTaskType;
import cn.redture.aiEngine.pojo.vo.AiTaskDetailVO;
import cn.redture.aiEngine.service.impl.AiTaskServiceImpl;
import cn.redture.common.exception.businessException.ResourceNotFoundException;
import cn.redture.common.integration.ai.AiExternalService;
import cn.redture.common.pojo.dto.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AiJobDetailAccessTest {
    @BeforeEach void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new UserPrincipal("1", "tester"), null));
    }
    @AfterEach void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @Test void detailBindsPublicIdFromHttpPath() throws Exception {
        AiTaskService tasks = mock(AiTaskService.class);
        AiTaskDetailVO detail = new AiTaskDetailVO(); detail.setPublicId("test-job");
        when(tasks.getTaskDetail("test-job")).thenReturn(detail);
        MockMvcBuilders.standaloneSetup(new AiJobController(tasks, mock(AiExternalService.class))).build()
                .perform(get("/ai/jobs/test-job")).andExpect(status().isOk());
        verify(tasks).getTaskDetail("test-job");
    }
    @Test void ownerCanReadDetail() {
        AiTaskMapper mapper = mock(AiTaskMapper.class);
        AiTask task = task(1L);
        when(mapper.selectOne(any())).thenReturn(task);
        assertEquals("test-job", new AiTaskServiceImpl(mapper).getTaskDetail("test-job").getPublicId());
    }
    @Test void otherUsersAndMissingTasksAreNotDisclosed() {
        AiTaskMapper mapper = mock(AiTaskMapper.class);
        when(mapper.selectOne(any())).thenReturn(task(2L)).thenReturn(null);
        AiTaskServiceImpl service = new AiTaskServiceImpl(mapper);
        assertThrows(ResourceNotFoundException.class, () -> service.getTaskDetail("test-job"));
        assertThrows(ResourceNotFoundException.class, () -> service.getTaskDetail("missing"));
    }
    private AiTask task(Long userId) {
        AiTask task = new AiTask(); task.setPublicId("test-job"); task.setUserId(userId);
        task.setTaskType(AiTaskType.POLISH); task.setTaskStatus(AiTaskStatus.COMPLETED); return task;
    }
}

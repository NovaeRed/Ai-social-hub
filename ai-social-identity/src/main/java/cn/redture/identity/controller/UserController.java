package cn.redture.identity.controller;

import cn.redture.common.pojo.model.RestResult;
import cn.redture.common.util.SecurityContextHolderUtil;
import cn.redture.common.event.ai.AiPersonaClearRequestedEvent;
import cn.redture.identity.pojo.dto.ChangePasswordDTO;
import cn.redture.identity.pojo.dto.UpdateUserDTO;
import cn.redture.identity.pojo.vo.UserInformation;
import cn.redture.identity.service.UserService;
import cn.redture.identity.service.UserLookupService;
import cn.redture.identity.pojo.vo.UserLookupResult;
import jakarta.annotation.Resource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
public class UserController {

    @Resource
    private UserService userService;
    @Resource
    private UserLookupService userLookupService;
    @Resource
    private ApplicationEventPublisher eventPublisher;

    @GetMapping("/me")
    public RestResult<UserInformation> getUserInformation() {
        Long userId = SecurityContextHolderUtil.getUserId();
        UserInformation userResult = userService.getUserById(userId);
        return RestResult.success(userResult);
    }

    @GetMapping("/{user_public_id}")
    public RestResult<UserInformation> getUserByPublicId(@PathVariable("user_public_id") String userPublicId) {
        UserInformation userResult = userService.getUserByPublicId(userPublicId);
        return RestResult.success(userResult);
    }

    @GetMapping("/lookup")
    public RestResult<UserLookupResult> lookup(@RequestParam("type") String type,
                                               @RequestParam("value") String value) {
        return RestResult.success(userLookupService.lookup(SecurityContextHolderUtil.getUserId(), type, value));
    }

    @PatchMapping("/me")
    public RestResult<UserInformation> updateUserInformation(@RequestBody UpdateUserDTO updateUserDTO) {
        UserInformation updatedUser = userService.updateUserInfo(updateUserDTO);
        return RestResult.success(updatedUser);
    }

    @DeleteMapping("/me/ai-persona")
    public RestResult<Void> clearAiPersona() {
        Long userId = SecurityContextHolderUtil.getUserId();
        eventPublisher.publishEvent(new AiPersonaClearRequestedEvent(userId));
        return RestResult.accepted(null);
    }

    @PostMapping("/me/password")
    public RestResult<Void> changePassword(@RequestBody ChangePasswordDTO changePasswordDTO) {
        Long userId = SecurityContextHolderUtil.getUserId();
        userService.changePassword(userId, changePasswordDTO.getCurrentPassword(), changePasswordDTO.getNewPassword());
        return RestResult.noContent();
    }
}

package cn.redture.identity.controller;

import cn.redture.common.model.RestResult;
import cn.redture.common.util.SecurityContextHolderUtil;
import cn.redture.identity.pojo.dto.UpdateUserDTO;
import cn.redture.identity.pojo.vo.UserInformation;
import cn.redture.identity.service.UserService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
public class UserController {

    @Resource
    private UserService userService;

    @GetMapping("/me")
    public RestResult<UserInformation> getUserInformation() {
        Long userId = SecurityContextHolderUtil.getUserId();
        UserInformation userResult = userService.getUserById(String.valueOf(userId));
        return RestResult.success(userResult);
    }

    @PatchMapping("/me")
    public RestResult<UserInformation> updateUserInformation(@RequestBody UpdateUserDTO updateUserDTO) {
        UserInformation updatedUser = userService.updateUserInfo(updateUserDTO);
        return RestResult.success(updatedUser);
    }
}

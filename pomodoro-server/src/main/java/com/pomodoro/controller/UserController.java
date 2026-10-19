package com.pomodoro.controller;

import com.pomodoro.common.Result;
import com.pomodoro.dto.user.DeleteAccountRequest;
import com.pomodoro.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户账号接口
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 注销账号（需密码二次确认，删除全部个人数据）
     */
    @PostMapping("/account/delete")
    public Result<Void> deleteAccount(@Valid @RequestBody DeleteAccountRequest request) {
        userService.deleteAccount(request.getPassword());
        return Result.success();
    }
}

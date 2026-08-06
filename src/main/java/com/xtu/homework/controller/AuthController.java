package com.xtu.homework.controller;

import com.xtu.homework.common.R;
import com.xtu.homework.dto.LoginDto;
import com.xtu.homework.service.UserService;
import com.xtu.homework.util.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    @PostMapping("/login")
    public R login(@Valid @RequestBody LoginDto dto) {
        try {
            String token = userService.login(dto.getUsername(), dto.getPassword());
            return R.ok().data(Map.of("token", token,
                    "userId", jwtUtil.getUserId(token),
                    "role", jwtUtil.getRole(token)));
        } catch (RuntimeException e) {
            return R.unauthorized(e.getMessage());
        }
    }

    @PostMapping("/logout")
    public R logout() {
        return R.ok("已退出");
    }

    @PutMapping("/password")
    public R changePassword(@RequestBody Map<String, String> body,
                            @RequestAttribute("userId") Long userId) {
        try {
            userService.changePassword(userId, body.get("oldPassword"), body.get("newPassword"));
            return R.ok("密码修改成功");
        } catch (RuntimeException e) {
            return R.badRequest(e.getMessage());
        }
    }

    @PutMapping("/profile")
    public R updateProfile(@RequestBody Map<String, String> body,
                           @RequestAttribute("userId") Long userId) {
        try {
            userService.updateProfile(userId, body.get("realName"), body.get("phone"), body.get("email"));
            return R.ok("个人信息已更新");
        } catch (RuntimeException e) {
            return R.badRequest(e.getMessage());
        }
    }

    @GetMapping("/me")
    public R currentUser(@RequestAttribute("userId") Long userId,
                         @RequestAttribute("role") String role) {
        var user = userService.getById(userId);
        return R.ok().data(Map.of(
                "userId", userId,
                "role", role,
                "username", user.getUsername(),
                "realName", user.getRealName(),
                "phone", user.getPhone() == null ? "" : user.getPhone(),
                "email", user.getEmail() == null ? "" : user.getEmail(),
                "pwdResetRequired", user.getPwdResetRequired() != null && user.getPwdResetRequired()));
    }
}

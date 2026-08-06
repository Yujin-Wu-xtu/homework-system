package com.xtu.homework.controller;

import com.xtu.homework.common.R;
import com.xtu.homework.dto.LoginDto;
import com.xtu.homework.service.EmailCodeSender;
import com.xtu.homework.service.UserService;
import com.xtu.homework.service.VerificationCodeService;
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
    private final VerificationCodeService verificationCodeService;
    private final EmailCodeSender emailCodeSender;

    @PostMapping("/login")
    public R login(@Valid @RequestBody LoginDto dto) {
        try {
            String token = userService.login(dto.getUsername(), dto.getPassword());
            Long userId = jwtUtil.getUserId(token);
            var user = userService.getById(userId);
            return R.ok().data(Map.of("token", token,
                    "userId", userId,
                    "role", jwtUtil.getRole(token),
                    "pwdResetRequired", user.getPwdResetRequired() != null && user.getPwdResetRequired()));
        } catch (RuntimeException e) {
            return R.unauthorized(e.getMessage());
        }
    }

    /** 发送邮箱注册验证码（60s 冷却 / 单邮箱日限 10 次） */
    @PostMapping("/verification-code/send")
    public R sendVerificationCode(@RequestBody Map<String, String> body) {
        String email = body == null ? null : body.get("email");
        if (email == null || email.isBlank()) {
            return R.badRequest("邮箱不能为空");
        }
        if (!email.trim().matches("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$")) {
            return R.badRequest("邮箱格式不正确");
        }
        // 已注册邮箱不允许再发验证码
        Long count = userService.count(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.xtu.homework.entity.User>()
                .eq(com.xtu.homework.entity.User::getEmail, email.trim()));
        if (count != null && count > 0) {
            return R.badRequest("该邮箱已注册，请直接登录");
        }
        try {
            String code = verificationCodeService.issue("email", email);
            emailCodeSender.sendCode(email.trim(), code);
            return R.ok("验证码已发送至 " + email.trim() + "，5 分钟内有效");
        } catch (RuntimeException e) {
            return R.badRequest(e.getMessage());
        }
    }

    /** 邮箱注册：用户名/邮箱/密码/验证码，注册为 STUDENT */
    @PostMapping("/register")
    public R register(@RequestBody Map<String, String> body) {
        try {
            userService.registerByEmail(body.get("username"), body.get("email"),
                    body.get("password"), body.get("code"));
            return R.ok("注册成功，请登录");
        } catch (RuntimeException e) {
            return R.badRequest(e.getMessage());
        }
    }

    @PostMapping("/logout")
    public R logout() {
        return R.ok("已退出");
    }

    @PutMapping("/password")
    public R changePassword(@RequestBody Map<String, String> body,
                            @RequestAttribute("userId") Long userId,
                            @RequestAttribute("role") String role) {
        try {
            // 学生仅允许"首次登录强制改密"（pwd_reset_required=true 时）；日常不可自助改密
            if ("STUDENT".equals(role)) {
                var user = userService.getById(userId);
                boolean forced = user.getPwdResetRequired() != null && user.getPwdResetRequired();
                if (!forced) {
                    return R.badRequest("学生账号不可自行修改密码，请联系管理员或任课教师重置");
                }
            }
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

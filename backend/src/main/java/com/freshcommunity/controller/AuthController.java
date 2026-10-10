package com.freshcommunity.controller;

import com.freshcommunity.common.Result;
import com.freshcommunity.dto.LoginRequest;
import com.freshcommunity.dto.LoginResponse;
import com.freshcommunity.dto.UserVO;
import com.freshcommunity.entity.User;
import com.freshcommunity.mapper.UserMapper;
import com.freshcommunity.security.JwtUtil;
import jakarta.validation.Valid;
import org.springframework.beans.BeanUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthController(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        User user = userMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .eq(User::getUsername, req.getUsername()));
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            return Result.error(401, "用户名或密码错误");
        }
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return Result.success(new LoginResponse(token, vo));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public Result<UserVO> me(@RequestAttribute("userId") Long userId) {
        User user = userMapper.selectById(userId);
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return Result.success(vo);
    }
}

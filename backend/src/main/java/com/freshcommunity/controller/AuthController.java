package com.freshcommunity.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.freshcommunity.common.Result;
import com.freshcommunity.common.UnauthorizedException;
import com.freshcommunity.dto.LoginRequest;
import com.freshcommunity.dto.LoginResponse;
import com.freshcommunity.dto.RegisterRequest;
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
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, req.getUsername()));
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            // 抛出异常以返回真正的 HTTP 401
            throw new UnauthorizedException("用户名或密码错误");
        }
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return Result.success(new LoginResponse(token, vo));
    }

    @PostMapping("/register")
    public Result<LoginResponse> register(@Valid @RequestBody RegisterRequest req) {
        String username = req.getUsername().trim();
        Long exists = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (exists != null && exists > 0) {
            throw new IllegalArgumentException("用户名已存在");
        }

        String role = (req.getRole() == null || req.getRole().isBlank())
                ? "USER" : req.getRole().trim().toUpperCase();
        if (!"USER".equals(role) && !"LEADER".equals(role)) {
            throw new IllegalArgumentException("仅支持注册普通用户或团长");
        }
        if ("LEADER".equals(role)
                && (req.getPickupPointName() == null || req.getPickupPointName().isBlank())) {
            throw new IllegalArgumentException("团长注册需填写提货点名称");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setRole(role);
        if ("LEADER".equals(role)) {
            user.setPickupPointName(req.getPickupPointName().trim());
            user.setPickupPointAddress(req.getPickupPointAddress());
        }
        userMapper.insert(user);

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

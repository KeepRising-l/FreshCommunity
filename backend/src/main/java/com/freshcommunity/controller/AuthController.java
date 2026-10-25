package com.freshcommunity.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.freshcommunity.common.Result;
import com.freshcommunity.common.UnauthorizedException;
import com.freshcommunity.dto.ChangePasswordRequest;
import com.freshcommunity.dto.LoginRequest;
import com.freshcommunity.dto.LoginResponse;
import com.freshcommunity.dto.ProfileUpdateRequest;
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
        if (user == null) {
            // 账号被逻辑删除后旧 token 仍在 24h 有效期内：明确 401 让前端走重新登录，而非 500
            throw new UnauthorizedException("账号不存在或已注销");
        }
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return Result.success(vo);
    }

    /**
     * 修改密码：旧密码必须匹配，新密码合规（长度 6-32）且不得与旧密码相同。
     * JWT 无黑名单，修改成功后旧 token 仍有效至过期——毕设口径，前端提示牢记新密码。
     */
    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> changePassword(@RequestAttribute("userId") Long userId,
                                       @Valid @RequestBody ChangePasswordRequest req) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new UnauthorizedException("账号不存在或已注销");
        }
        if (!passwordEncoder.matches(req.getOldPassword(), user.getPassword())) {
            throw new IllegalArgumentException("旧密码错误");
        }
        if (req.getOldPassword().equals(req.getNewPassword())) {
            throw new IllegalArgumentException("新密码不能与旧密码相同");
        }
        User update = new User();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userMapper.updateById(update);
        return Result.success();
    }

    /**
     * 个人资料维护：头像（avatarUrl）全角色可写；提货点三项（名称/地址/照片）仅团长。
     * 图片地址必须是本站路径（/uploads/ 或 /images/ 前缀），防塞外链脚本。
     * 修改即时生效于其名下团购详情（详情读时实时取 user 表）。
     */
    @PutMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    public Result<UserVO> updateProfile(@RequestAttribute("userId") Long userId,
                                        @RequestAttribute("role") String role,
                                        @Valid @RequestBody ProfileUpdateRequest req) {
        User update = new User();
        update.setId(userId);

        // 头像：全角色
        String avatar = req.getAvatarUrl();
        if (avatar != null && !avatar.isBlank()) {
            avatar = avatar.trim();
            requireSiteImagePath(avatar, "头像地址");
            update.setAvatarUrl(avatar);
        }

        // 提货点三项：仅团长
        boolean touchesPickup = req.getPickupPointName() != null
                || req.getPickupPointAddress() != null || req.getPickupPointImageUrl() != null;
        if (touchesPickup) {
            if (!"LEADER".equals(role)) {
                throw new IllegalArgumentException("仅团长可维护提货点信息");
            }
            String name = req.getPickupPointName() == null ? null : req.getPickupPointName().trim();
            if (name != null) {
                if (name.isEmpty()) {
                    throw new IllegalArgumentException("提货点名称不能为空");
                }
                update.setPickupPointName(name);
            }
            if (req.getPickupPointAddress() != null) {
                update.setPickupPointAddress(req.getPickupPointAddress().trim());
            }
            String photo = req.getPickupPointImageUrl();
            if (photo != null && !photo.isBlank()) {
                photo = photo.trim();
                requireSiteImagePath(photo, "提货点照片地址");
                update.setPickupPointImageUrl(photo);
            }
        }

        userMapper.updateById(update);
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(userMapper.selectById(userId), vo);
        return Result.success(vo);
    }

    /** 图片地址必须是本站静态路径，拒绝外链与非图片路径 */
    private void requireSiteImagePath(String url, String fieldLabel) {
        if (!url.startsWith("/uploads/") && !url.startsWith("/images/")) {
            throw new IllegalArgumentException(fieldLabel + "必须是本站图片路径");
        }
    }
}

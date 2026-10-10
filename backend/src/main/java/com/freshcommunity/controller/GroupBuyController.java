package com.freshcommunity.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.freshcommunity.common.Result;
import com.freshcommunity.dto.GroupBuyCreateRequest;
import com.freshcommunity.dto.UserVO;
import com.freshcommunity.entity.GroupBuy;
import com.freshcommunity.entity.User;
import com.freshcommunity.mapper.UserMapper;
import com.freshcommunity.service.GroupBuyService;
import jakarta.validation.Valid;
import lombok.Data;
import org.springframework.beans.BeanUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/group-buys")
public class GroupBuyController {

    private final GroupBuyService groupBuyService;
    private final UserMapper userMapper;

    public GroupBuyController(GroupBuyService groupBuyService, UserMapper userMapper) {
        this.groupBuyService = groupBuyService;
        this.userMapper = userMapper;
    }

    @GetMapping
    public Result<List<GroupBuy>> list() {
        return Result.success(groupBuyService.listOngoing());
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('LEADER')")
    public Result<List<GroupBuy>> mine(@RequestAttribute("userId") Long userId) {
        return Result.success(groupBuyService.listByLeader(userId));
    }

    @GetMapping("/{id}")
    public Result<GroupBuyDetailVO> detail(@PathVariable Long id) {
        GroupBuy gb = groupBuyService.detail(id);
        if (gb == null) {
            throw new IllegalArgumentException("团购不存在");
        }
        User leader = userMapper.selectById(gb.getLeaderId());
        GroupBuyDetailVO vo = new GroupBuyDetailVO();
        BeanUtils.copyProperties(gb, vo);
        if (leader != null) {
            vo.setLeaderName(leader.getUsername());
            vo.setPickupPointName(leader.getPickupPointName());
            vo.setPickupPointAddress(leader.getPickupPointAddress());
        }
        return Result.success(vo);
    }

    @PostMapping
    @PreAuthorize("hasRole('LEADER')")
    public Result<Long> create(@Valid @RequestBody GroupBuyCreateRequest req,
                               @RequestAttribute("userId") Long userId) {
        GroupBuy gb = groupBuyService.createGroupBuy(userId, req);
        return Result.success(gb.getId());
    }

    @lombok.Data
    public static class GroupBuyDetailVO {
        private Long id;
        private Long leaderId;
        private String title;
        private Integer targetCount;
        private java.time.LocalDateTime deadline;
        private String status;
        private Integer memberCount;
        private String leaderName;
        private String pickupPointName;
        private String pickupPointAddress;
        private java.time.LocalDateTime createdAt;
    }
}

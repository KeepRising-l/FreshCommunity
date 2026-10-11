package com.freshcommunity.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.freshcommunity.common.Result;
import com.freshcommunity.dto.GroupBuyCreateRequest;
import com.freshcommunity.dto.GroupBuyListVO;
import com.freshcommunity.dto.RatingVO;
import com.freshcommunity.dto.UserVO;
import com.freshcommunity.entity.GroupBuy;
import com.freshcommunity.entity.Order;
import com.freshcommunity.entity.User;
import com.freshcommunity.mapper.UserMapper;
import com.freshcommunity.service.GroupBuyService;
import com.freshcommunity.service.ReviewService;
import jakarta.validation.Valid;
import lombok.Data;
import org.springframework.beans.BeanUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/group-buys")
public class GroupBuyController {

    private final GroupBuyService groupBuyService;
    private final UserMapper userMapper;
    private final ReviewService reviewService;

    public GroupBuyController(GroupBuyService groupBuyService, UserMapper userMapper,
                              ReviewService reviewService) {
        this.groupBuyService = groupBuyService;
        this.userMapper = userMapper;
        this.reviewService = reviewService;
    }

    @GetMapping
    public Result<List<GroupBuyListVO>> list() {
        List<GroupBuy> groups = groupBuyService.listOngoing();
        if (groups.isEmpty()) {
            return Result.success(Collections.emptyList());
        }
        Set<Long> leaderIds = groups.stream().map(GroupBuy::getLeaderId).collect(Collectors.toSet());
        Map<Long, User> leaders = userMapper.selectBatchIds(leaderIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        List<GroupBuyListVO> vos = new ArrayList<>();
        for (GroupBuy g : groups) {
            GroupBuyListVO vo = new GroupBuyListVO();
            BeanUtils.copyProperties(g, vo);
            User leader = leaders.get(g.getLeaderId());
            if (leader != null) {
                vo.setLeaderName(leader.getUsername());
                vo.setPickupPointName(leader.getPickupPointName());
                vo.setPickupPointAddress(leader.getPickupPointAddress());
            }
            vos.add(vo);
        }
        return Result.success(vos);
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
            RatingVO rating = reviewService.leaderRating(gb.getLeaderId());
            vo.setLeaderAvgScore(rating.getAvgScore());
            vo.setLeaderReviewCount(rating.getCount());
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

    @GetMapping("/{id}/orders")
    @PreAuthorize("hasRole('LEADER')")
    public Result<List<Order>> orders(@PathVariable Long id,
                                      @RequestAttribute("userId") Long leaderId) {
        return Result.success(groupBuyService.listOrdersForLeader(id, leaderId));
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
        /** 团长评分汇总（来自已核销订单的评价） */
        private Double leaderAvgScore;
        private Long leaderReviewCount;
    }
}

package com.freshcommunity.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.freshcommunity.common.Result;
import com.freshcommunity.dto.GroupBuyCreateRequest;
import com.freshcommunity.dto.GroupBuyListVO;
import com.freshcommunity.dto.RatingVO;
import com.freshcommunity.dto.UserVO;
import com.freshcommunity.entity.GroupBuy;
import com.freshcommunity.entity.GroupBuyItem;
import com.freshcommunity.entity.Order;
import com.freshcommunity.entity.Product;
import com.freshcommunity.entity.User;
import com.freshcommunity.mapper.GroupBuyItemMapper;
import com.freshcommunity.mapper.ProductMapper;
import com.freshcommunity.mapper.UserMapper;
import com.freshcommunity.service.GroupBuyService;
import com.freshcommunity.service.ReviewAnalysisService;
import com.freshcommunity.service.ReviewService;
import jakarta.validation.Valid;
import lombok.Data;
import org.springframework.beans.BeanUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
    private final ReviewAnalysisService reviewAnalysisService;
    private final GroupBuyItemMapper groupBuyItemMapper;
    private final ProductMapper productMapper;

    public GroupBuyController(GroupBuyService groupBuyService, UserMapper userMapper,
                              ReviewService reviewService, ReviewAnalysisService reviewAnalysisService,
                              GroupBuyItemMapper groupBuyItemMapper,
                              ProductMapper productMapper) {
        this.groupBuyService = groupBuyService;
        this.userMapper = userMapper;
        this.reviewService = reviewService;
        this.reviewAnalysisService = reviewAnalysisService;
        this.groupBuyItemMapper = groupBuyItemMapper;
        this.productMapper = productMapper;
    }

    /**
     * 填充团购所挂商品信息（拼团价、原价、图片）。
     * 团购本身不存价格：拼团价取自 group_buy_item.price_snapshot，原价与图片取自 product。
     */
    private void fillProduct(GroupBuyListVO vo, GroupBuyItem item, Product product) {
        if (item == null) {
            return;
        }
        vo.setProductId(item.getProductId());
        vo.setPrice(item.getPriceSnapshot());
        if (product != null) {
            vo.setProductName(product.getName());
            vo.setImageUrl(product.getImageUrl());
            vo.setOriginalPrice(product.getPrice());
        }
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

        // 团购所挂商品：一个团购目前只挂一个商品，取第一条（与 OrderService.createOrder 口径一致）
        List<Long> groupIds = groups.stream().map(GroupBuy::getId).toList();
        Map<Long, GroupBuyItem> itemByGroup = groupBuyItemMapper
                .selectList(new LambdaQueryWrapper<GroupBuyItem>().in(GroupBuyItem::getGroupBuyId, groupIds))
                .stream()
                .collect(Collectors.toMap(GroupBuyItem::getGroupBuyId, i -> i, (a, b) -> a));

        Map<Long, Product> products = Collections.emptyMap();
        Map<Long, List<String>> tagsByProduct = new HashMap<>();
        Set<Long> productIds = itemByGroup.values().stream()
                .map(GroupBuyItem::getProductId)
                .collect(Collectors.toSet());
        if (!productIds.isEmpty()) {
            products = productMapper.selectBatchIds(productIds).stream()
                    .collect(Collectors.toMap(Product::getId, p -> p));
            for (Long pid : productIds) {
                tagsByProduct.put(pid, reviewAnalysisService.topTags(pid, 5));
            }
        }

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
            GroupBuyItem item = itemByGroup.get(g.getId());
            fillProduct(vo, item, item == null ? null : products.get(item.getProductId()));
            if (item != null) {
                vo.setInsightTags(tagsByProduct.getOrDefault(item.getProductId(), List.of()));
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

        GroupBuyItem item = groupBuyItemMapper.selectOne(
                new LambdaQueryWrapper<GroupBuyItem>()
                        .eq(GroupBuyItem::getGroupBuyId, id)
                        .last("LIMIT 1"));
        if (item != null) {
            fillProduct(vo, item, productMapper.selectById(item.getProductId()));
            vo.setInsightTags(reviewAnalysisService.topTags(item.getProductId(), 5));
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

    /**
     * 团购详情。字段是列表项的父集（含商品与价格），另加团长评分汇总。
     */
    @lombok.Data
    @lombok.EqualsAndHashCode(callSuper = true)
    public static class GroupBuyDetailVO extends GroupBuyListVO {
        /** 团长评分汇总（来自已核销订单的评价） */
        private Double leaderAvgScore;
        private Long leaderReviewCount;
    }
}

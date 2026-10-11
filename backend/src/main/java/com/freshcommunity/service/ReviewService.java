package com.freshcommunity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.freshcommunity.dto.RatingVO;
import com.freshcommunity.dto.ReviewCreateRequest;
import com.freshcommunity.dto.ReviewVO;
import com.freshcommunity.entity.GroupBuy;
import com.freshcommunity.entity.Order;
import com.freshcommunity.entity.Review;
import com.freshcommunity.mapper.GroupBuyMapper;
import com.freshcommunity.mapper.OrderMapper;
import com.freshcommunity.mapper.ReviewMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ReviewService {

    private final ReviewMapper reviewMapper;
    private final OrderMapper orderMapper;
    private final GroupBuyMapper groupBuyMapper;

    public ReviewService(ReviewMapper reviewMapper, OrderMapper orderMapper, GroupBuyMapper groupBuyMapper) {
        this.reviewMapper = reviewMapper;
        this.orderMapper = orderMapper;
        this.groupBuyMapper = groupBuyMapper;
    }

    /**
     * 提交评价。资格 = 订单属于本人且状态为 COMPLETED，且该订单尚无评价。
     * 并发重复提交由 review.order_id 唯一索引兜底（DuplicateKeyException → 400）。
     */
    @Transactional
    public Review submit(Long userId, ReviewCreateRequest req) {
        Order order = orderMapper.selectById(req.getOrderId());
        if (order == null || !order.getUserId().equals(userId)) {
            throw new IllegalArgumentException("订单不存在");
        }
        if (!"COMPLETED".equals(order.getStatus())) {
            throw new IllegalArgumentException("该订单不可评价");
        }
        Long exists = reviewMapper.selectCount(
                new LambdaQueryWrapper<Review>().eq(Review::getOrderId, order.getId()));
        if (exists != null && exists > 0) {
            throw new IllegalArgumentException("该订单已评价");
        }
        GroupBuy groupBuy = groupBuyMapper.selectById(order.getGroupBuyId());
        if (groupBuy == null) {
            throw new IllegalArgumentException("团购不存在");
        }

        Review review = new Review();
        review.setOrderId(order.getId());
        review.setUserId(userId);
        review.setProductId(order.getProductId());
        review.setLeaderId(groupBuy.getLeaderId());
        review.setProductScore(req.getProductScore());
        review.setLeaderScore(req.getLeaderScore());
        review.setContent(req.getContent() == null ? null : req.getContent().trim());
        reviewMapper.insert(review);
        return review;
    }

    public List<Review> listByUser(Long userId) {
        return reviewMapper.selectList(new LambdaQueryWrapper<Review>()
                .eq(Review::getUserId, userId)
                .orderByDesc(Review::getCreatedAt));
    }

    public List<ReviewVO> listVOByProduct(Long productId) {
        return reviewMapper.listVOByProduct(productId);
    }

    public RatingVO productRating(Long productId) {
        long count = reviewMapper.countByProduct(productId);
        return new RatingVO(count == 0 ? null : round1(reviewMapper.avgProductScore(productId)), count);
    }

    public RatingVO leaderRating(Long leaderId) {
        long count = reviewMapper.countByLeader(leaderId);
        return new RatingVO(count == 0 ? null : round1(reviewMapper.avgLeaderScore(leaderId)), count);
    }

    /** 供订单列表标记"是否已评价"，避免逐单查询。 */
    public List<Long> reviewedOrderIds(Long userId) {
        return reviewMapper.findOrderIdsByUser(userId);
    }

    private Double round1(Double value) {
        if (value == null) {
            return null;
        }
        return Math.round(value * 10.0) / 10.0;
    }
}

package com.freshcommunity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ProductReviewsVO {
    private RatingVO rating;
    private List<ReviewVO> reviews;
}

package com.freshcommunity.service;

import com.freshcommunity.entity.Order;

public interface PayService {
    boolean pay(Order order);
    boolean refund(Order order);
}

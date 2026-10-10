package com.freshcommunity.service;

import com.freshcommunity.entity.Order;
import org.springframework.stereotype.Service;

@Service
public class MockPayServiceImpl implements PayService {

    @Override
    public boolean pay(Order order) {
        return true;
    }

    @Override
    public boolean refund(Order order) {
        return true;
    }
}

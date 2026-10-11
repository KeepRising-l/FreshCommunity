package com.freshcommunity.common;

/**
 * 认证失败异常。用于让登录失败等场景返回真正的 HTTP 401，
 * 而不是 HTTP 200 + body code=401。
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}

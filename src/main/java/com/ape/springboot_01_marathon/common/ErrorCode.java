package com.ape.springboot_01_marathon.common;

/**
 * 业务错误码。
 * <p>
 * 约定：1xxxx 段归「登录与权限」，2xxxx 段归「业务校验」，500 兜底。
 * 前端拿到 code 可以区分是哪种失败。
 */
public final class ErrorCode {

    private ErrorCode() {
    }

    /** 未登录或 Token 已失效 */
    public static final int UNAUTHORIZED = 40100;

    /** 已登录但权限不足 */
    public static final int FORBIDDEN = 40300;

    /** 账号或密码错误 */
    public static final int LOGIN_FAILED = 40101;

    /** Token 无效 */
    public static final int TOKEN_INVALID = 40102;

    /** 账号被锁定（预留） */
    public static final int ACCOUNT_LOCKED = 40103;
}
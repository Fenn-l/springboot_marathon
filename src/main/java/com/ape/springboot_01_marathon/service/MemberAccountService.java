package com.ape.springboot_01_marathon.service;

import java.util.Map;

/**
 * B 端：选手登录账号管理。
 * <p>
 * 与 {@link MemberService}（C 端选手自助）分开：
 * 那边选手改密要验原密码，这边管理员直接重置 —— 管理员是可信方。
 */
public interface MemberAccountService {

    /**
     * 查询账号状态。
     *
     * @return {exists: 是否已开通, phone: 登录手机号, runnerName: 选手姓名}
     */
    Map<String, Object> status(Long runnerId);

    /**
     * 开通账号或重置密码（幂等）。
     * <p>
     * 账号不存在 → 开通，用选手档案里的手机号作为登录账号；
     * 账号已存在 → 重置密码，登录手机号保持不变。
     *
     * @return {created: true=新开通 false=重置, phone: 登录手机号}
     */
    Map<String, Object> resetPassword(Long runnerId, String password);
}

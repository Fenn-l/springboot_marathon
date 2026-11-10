package com.ape.springboot_01_marathon.dao;

import com.ape.springboot_01_marathon.pojo.Admin;
import org.apache.ibatis.annotations.Param;

public interface AdminMapper {

    /**
     * 按账号查管理员。
     * <p>
     * <b>注意</b>：这里不能用 {@code WHERE username=? AND password=?}。
     * BCrypt 每次加密结果都不同（自带随机盐），SQL 里没法直接比对密文，
     * 只能查出账号对应的密文，再在 Java 里用 encoder.matches() 验证。
     *
     * @param username 账号
     * @return 管理员，不存在返回 null
     */
    Admin selectByUsername(@Param("username") String username);

    /**
     * 更新登录时间（可选，用于记录最后登录）
     */
    int updateLoginTime(@Param("id") Long id);
}
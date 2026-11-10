package com.ape.springboot_01_marathon.dao;

import com.ape.springboot_01_marathon.pojo.MemberAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MemberAccountMapper {

    /** 按手机号查账号 */
    MemberAccount selectByPhone(@Param("phone") String phone);

    /** 按选手 id 查账号 */
    MemberAccount selectByRunnerId(@Param("runnerId") Long runnerId);

    /** 新增账号，password 传 BCrypt 密文 */
    int insert(MemberAccount account);

    /**
     * 更新密码，password 传 BCrypt 密文。
     * <p>
     * 用 runner_id 定位而不是 account id —— 改密是从「当前登录的选手」出发的，
     * 选手 id 由服务端从 Token 取，不接受前端传。
     */
    int updatePassword(@Param("runnerId") Long runnerId, @Param("password") String password);
}

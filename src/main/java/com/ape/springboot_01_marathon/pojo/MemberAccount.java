package com.ape.springboot_01_marathon.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * C 端选手账号。
 * <p>
 * <b>为什么单独建表而不是在 runner 上加 password 字段：</b>
 * <ul>
 *   <li>runner 表是「选手档案」，由 B 端管理员维护，可能一条档案对应多人共用手机号
 *       （现实中「单位统一报名」很常见）；</li>
 *   <li>手机号在 C 端是登录账号，必须唯一；而 runner.phone 并不唯一，
 *       直接加唯一索引会破坏既有数据；</li>
 *   <li>账号与档案 1:1，通过 runner_id 关联，两边职责清晰：
 *       档案存比赛信息，账号存登录凭据。</li>
 * </ul>
 */
@Data
public class MemberAccount {

    private Long id;

    /** 登录手机号，唯一 */
    private String phone;

    /** BCrypt 密文，序列化时忽略，绝不能吐给前端 */
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String password;

    /** 关联的选手档案 id */
    private Long runnerId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}

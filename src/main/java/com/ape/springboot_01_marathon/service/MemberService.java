package com.ape.springboot_01_marathon.service;

import com.ape.springboot_01_marathon.pojo.dto.MemberRegisterDTO;
import com.ape.springboot_01_marathon.pojo.Runner;
import com.ape.springboot_01_marathon.pojo.vo.MyRegistrationVO;
import com.ape.springboot_01_marathon.pojo.vo.MyResultVO;
import com.ape.springboot_01_marathon.pojo.vo.PublicEventVO;
import com.ape.springboot_01_marathon.pojo.vo.RankVO;

import java.util.List;
import java.util.Map;

/**
 * C 端（移动端 · 面向选手）业务。
 * <p>
 * 与管理端 {@link MarathonEventService} 等是<b>并列</b>的另一套视角：
 * 同一份数据，但只暴露「选手自己该看到的」那部分。
 */
public interface MemberService {

    /** 注册：建档案 + 建账号，返回选手信息与 Token */
    Map<String, Object> register(MemberRegisterDTO dto);

    /** 登录，返回选手信息与 Token */
    Map<String, Object> login(String phone, String password);

    /** 注销登录 */
    void logout(String token);

    /**
     * 修改登录密码。
     * <p>
     * 必须校验原密码 —— 否则拿到 Token 的人就能直接把密码改掉。
     *
     * @param runnerId  当前登录的选手 id（服务端从 Token 取）
     * @param oldPassword 原密码
     * @param newPassword 新密码
     */
    void changePassword(Long runnerId, String oldPassword, String newPassword);

    /** 我的资料（仅自己，全字段） */
    Runner profile(Long runnerId);

    /** 修改我的资料 */
    boolean updateProfile(Long runnerId, Runner runner);

    /** 公开赛事列表。未登录也能查，登录后会带上「我是否已报名」 */
    List<PublicEventVO> publicEvents(Long runnerId);

    /** 公开赛事详情 */
    PublicEventVO publicEventDetail(Long eventId, Long runnerId);

    /** 我的报名列表 */
    List<MyRegistrationVO> myRegistrations(Long runnerId);

    /** 我要报名 */
    Long registerEvent(Long runnerId, Long eventId);

    /** 取消我的报名 */
    boolean cancelMyRegistration(Long runnerId, Long registrationId);

    /** 我的成绩 */
    List<MyResultVO> myResults(Long runnerId);

    /** 我的完赛证书 */
    MyResultVO certificate(Long runnerId, Long registrationId);

    /** 公开排行榜：total / gender / age */
    Map<String, Object> publicRankings(Long eventId, String group);

    /**
     * 从 Token 解析选手 id，供公开接口「尽力识别当前用户」用。
     * <p>
     * Token 无效或不是选手 Token 时返回 null（视为游客），<b>不抛异常</b> ——
     * 公开接口对游客开放，不能因为带了张旧 Token 就把人挡在门外。
     */
    Long resolveRunnerId(String token);
}

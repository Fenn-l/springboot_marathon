package com.ape.springboot_01_marathon.service;

import com.ape.springboot_01_marathon.pojo.vo.MyResultVO;

import java.util.List;
import java.util.Map;

/**
 * B 端现场运营：检录核销 + 证书签发。
 * <p>
 * 对标调研中「现场工作台」的模式：<b>只显示当前岗位需要的任务</b>。
 * 检录页只管「这个号码布能不能进赛道」，不管别的。
 */
public interface CheckinService {

    /**
     * 检录核销。
     *
     * @param registrationId 报名记录 id
     * @param bibNumber      号码布（二选一传）
     * @return 核销结果
     */
    Map<String, Object> checkin(Long registrationId, String bibNumber);

    /**
     * 某赛事的检录进度。
     */
    Map<String, Object> progress(Long eventId);

    /**
     * 某赛事可签发证书的名单（已完赛的）。
     */
    List<MyResultVO> certificateList(Long eventId);

    /**
     * 批量签发证书（这里只是打标记，不落独立证书表）。
     *
     * @return 签发条数
     */
    int issueCertificates(Long eventId);
}

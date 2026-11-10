package com.ape.springboot_01_marathon.service.impl;

import com.ape.springboot_01_marathon.common.BusinessException;
import com.ape.springboot_01_marathon.common.EventStatus;
import com.ape.springboot_01_marathon.common.MyUtil;
import com.ape.springboot_01_marathon.dao.EventMapper;
import com.ape.springboot_01_marathon.dao.RegistrationMapper;
import com.ape.springboot_01_marathon.dao.ResultMapper;
import com.ape.springboot_01_marathon.dao.RunnerMapper;
import com.ape.springboot_01_marathon.pojo.MarathonEvent;
import com.ape.springboot_01_marathon.pojo.Registration;
import com.ape.springboot_01_marathon.pojo.Runner;
import com.ape.springboot_01_marathon.pojo.vo.MyResultVO;
import com.ape.springboot_01_marathon.service.CheckinService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * B 端现场运营实现：检录核销 + 证书签发。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckinServiceImpl implements CheckinService {

    private final RegistrationMapper registrationMapper;
    private final ResultMapper resultMapper;
    private final RunnerMapper runnerMapper;
    private final EventMapper eventMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> checkin(Long registrationId, String bibNumber) {
        // 报名 id 与号码布二选一
        Long regId = registrationId;
        if (regId == null) {
            if (bibNumber == null || bibNumber.isBlank()) {
                throw new BusinessException("请提供报名 ID 或号码布");
            }
            regId = registrationMapper.selectIdByBib(bibNumber.trim());
            if (regId == null) {
                throw new BusinessException(404, "号码布 " + bibNumber.trim() + " 不存在");
            }
        }

        Registration reg = registrationMapper.selectById(regId);
        if (reg == null) {
            throw new BusinessException(404, "报名记录不存在");
        }
        if (reg.getStatus() != 1) {
            throw new BusinessException("该报名已取消或退赛，不能检录");
        }

        // 赛事状态门禁：还在报名中说明没开赛，赛后也不该再核销
        assertEventState(reg.getEventId());

        if (reg.getCheckinTime() != null) {
            // 重复核销不算错误，提示一下即可，现场手抖点两次很正常
            Map<String, Object> data = buildCheckinResult(reg, true);
            data.put("message", "该选手已检录过");
            return data;
        }

        int rows = registrationMapper.markCheckin(regId);
        if (rows == 0) {
            throw new BusinessException("检录失败，记录状态已变更");
        }
        log.info("检录核销成功 registrationId={} bib={}", regId, reg.getBibNumber());

        reg = registrationMapper.selectById(regId);
        return buildCheckinResult(reg, false);
    }

    /** 组装检录结果：只给现场需要的字段 */
    private Map<String, Object> buildCheckinResult(Registration reg, boolean alreadyCheckedIn) {
        Runner runner = runnerMapper.selectById(reg.getRunnerId());
        Map<String, Object> data = new HashMap<>(8);
        data.put("registrationId", reg.getId());
        data.put("bibNumber", reg.getBibNumber());
        data.put("name", runner == null ? null : runner.getName());
        // 现场核验只需要「是不是本人」，不需要看完整身份证
        data.put("idCardTail", runner == null ? null : tailId(runner.getIdCard()));
        data.put("gender", runner == null ? null : runner.getGender());
        data.put("checkedIn", true);
        data.put("alreadyCheckedIn", alreadyCheckedIn);
        data.put("checkinTime", reg.getCheckinTime());
        return data;
    }

    /** 身份证只给后 4 位 */
    private String tailId(String idCard) {
        if (idCard == null || idCard.length() < 4) {
            return idCard;
        }
        return "****" + idCard.substring(idCard.length() - 4);
    }

    @Override
    public Map<String, Object> progress(Long eventId) {
        int total = registrationMapper.countActiveByEvent(eventId);
        int checked = registrationMapper.countCheckinByEvent(eventId);
        Map<String, Object> data = new HashMap<>(4);
        data.put("total", total);
        data.put("checked", checked);
        data.put("unchecked", Math.max(0, total - checked));
        data.put("percent", total == 0 ? 0 : Math.round(checked * 100.0 / total));
        return data;
    }

    @Override
    public List<MyResultVO> certificateList(Long eventId) {
        List<MyResultVO> list = resultMapper.selectCertificateList(eventId);
        for (MyResultVO vo : list) {
            vo.setNetTimeStr(MyUtil.formatSeconds(vo.getNetTime()));
        }
        return list;
    }

    @Override
    public int issueCertificates(Long eventId) {
        // 证书只能在赛事结束后签发 —— 成绩还没定就发证是荒谬的
        MarathonEvent event = eventMapper.selectById(eventId);
        if (event == null) {
            throw new BusinessException(404, "赛事不存在");
        }
        EventStatus.assertCanIssueCertificate(event.getStatus());

        int rows = resultMapper.issueCertificates(eventId);
        log.info("赛事 {} 批量签发证书 {} 份", eventId, rows);
        return rows;
    }

    /** 赛事状态门禁：检录只在「报名截止」和「比赛中」两个阶段开放 */
    private void assertEventState(Long eventId) {
        MarathonEvent event = eventMapper.selectById(eventId);
        if (event == null) {
            throw new BusinessException(404, "赛事不存在");
        }
        EventStatus.assertCanCheckin(event.getStatus());
    }
}

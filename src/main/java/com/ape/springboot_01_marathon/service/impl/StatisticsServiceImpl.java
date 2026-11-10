package com.ape.springboot_01_marathon.service.impl;

import com.ape.springboot_01_marathon.common.BusinessException;
import com.ape.springboot_01_marathon.dao.EventMapper;
import com.ape.springboot_01_marathon.dao.RegistrationMapper;
import com.ape.springboot_01_marathon.dao.ResultMapper;
import com.ape.springboot_01_marathon.pojo.MarathonEvent;
import com.ape.springboot_01_marathon.pojo.vo.EventStatisticsVO;
import com.ape.springboot_01_marathon.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final EventMapper eventMapper;
    private final RegistrationMapper registrationMapper;
    private final ResultMapper resultMapper;

    @Override
    public EventStatisticsVO eventOverview(Long eventId) {
        if (eventId == null) {
            throw new BusinessException("赛事ID不能为空");
        }

        MarathonEvent event = eventMapper.selectById(eventId);
        if (event == null) {
            throw new BusinessException(404, "赛事不存在");
        }

        EventStatisticsVO vo = new EventStatisticsVO();
        vo.setEventId(event.getId());
        vo.setEventName(event.getEventName());

        // 报名人数 + 男女分布
        int totalRegister = registrationMapper.countRegisterByEvent(eventId);
        vo.setTotalRegister(totalRegister);
        vo.setMaleCount(registrationMapper.countRegisterByGender(eventId, 1));
        vo.setFemaleCount(registrationMapper.countRegisterByGender(eventId, 2));

        // 三种完赛状态
        vo.setFinishCount(resultMapper.countByFinishStatus(eventId, 1));
        vo.setUnFinishCount(resultMapper.countByFinishStatus(eventId, 2));
        vo.setRetireCount(resultMapper.countByFinishStatus(eventId, 3));

        // 完赛率
        vo.calcFinishRate();

        // 平均净成绩。
        // 注意：没有完赛成绩时 avgNetTime 返回 0（SQL 里 IFNULL 兜的底），
        // 这里转成 null，让前端显示 "--" 而不是 "00:00:00"。
        // setAvgNetTime 内部会同步处理 avgNetTimeStr。
        Double avg = resultMapper.avgNetTime(eventId);
        Integer avgInt = null;
        if (avg != null && avg > 0) {
            avgInt = BigDecimal.valueOf(avg).setScale(0, RoundingMode.HALF_UP).intValue();
        }
        vo.setAvgNetTime(avgInt);

        log.info("赛事 {} 统计完成：报名 {} 人，完赛 {} 人，平均成绩 {}",
                eventId, totalRegister, vo.getFinishCount(), vo.getAvgNetTimeStr());
        return vo;
    }
}


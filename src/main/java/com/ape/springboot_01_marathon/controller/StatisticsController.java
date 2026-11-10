package com.ape.springboot_01_marathon.controller;

import com.ape.springboot_01_marathon.common.Result;
import com.ape.springboot_01_marathon.pojo.vo.EventStatisticsVO;
import com.ape.springboot_01_marathon.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsService statisticsService;

    /** 单场赛事统计总览 */
    @GetMapping("/eventOverview/{eventId}")
    public Result<EventStatisticsVO> eventOverview(@PathVariable Long eventId) {
        return Result.success(statisticsService.eventOverview(eventId));
    }
}

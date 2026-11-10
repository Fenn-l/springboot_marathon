package com.ape.springboot_01_marathon.service;

import com.ape.springboot_01_marathon.pojo.vo.EventStatisticsVO;

public interface StatisticsService {

    EventStatisticsVO eventOverview(Long eventId);
}


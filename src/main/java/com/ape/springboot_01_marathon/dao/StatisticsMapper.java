package com.ape.springboot_01_marathon.dao;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface StatisticsMapper {

    /** 各完赛状态人数：[{finishStatus:1, cnt:2100}, ...] */
    List<Map<String, Object>> countGroupByFinishStatus(@Param("eventId") Long eventId);

    /** 男女报名分布 */
    List<Map<String, Object>> countGroupByGender(@Param("eventId") Long eventId);
}


package com.ape.springboot_01_marathon.dao;

import com.ape.springboot_01_marathon.pojo.MarathonEvent;
import com.ape.springboot_01_marathon.pojo.dto.EventQuery;
import com.ape.springboot_01_marathon.pojo.vo.EventVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface EventMapper {

    int insert(MarathonEvent event);

    int update(MarathonEvent event);

    int deleteById(@Param("id") Long id);

    /** 列表查询：返回带 remainQuota 的 VO */
    List<EventVO> selectPage(EventQuery query);

    long countPage(EventQuery query);

    /** 详情：单个 VO */
    EventVO selectDetailById(@Param("id") Long id);

    /** 只取实体，报名校验时用 */
    MarathonEvent selectById(@Param("id") Long id);

    /** 报名成功 +1 */
    int increaseRegisteredCount(@Param("id") Long id);

    /** 取消报名 -1，quota-registered_count>0 作为条件防止变负数 */
    int decreaseRegisteredCount(@Param("id") Long id);

    /** 该赛事下的有效报名数，删除赛事前判断用 */
    int countActiveRegistration(@Param("eventId") Long eventId);
}


package com.ape.springboot_01_marathon.service;

import com.ape.springboot_01_marathon.common.PageResult;
import com.ape.springboot_01_marathon.pojo.MarathonEvent;
import com.ape.springboot_01_marathon.pojo.dto.EventQuery;
import com.ape.springboot_01_marathon.pojo.vo.EventVO;

public interface MarathonEventService {

    Long add(MarathonEvent event);

    boolean update(MarathonEvent event);

    boolean delete(Long id);

    /** 分页查询，返回 PageResult<EventVO> */
    PageResult<EventVO> page(EventQuery query);

    EventVO detail(Long id);
}


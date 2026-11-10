package com.ape.springboot_01_marathon.service;

import com.ape.springboot_01_marathon.common.PageResult;
import com.ape.springboot_01_marathon.pojo.ResultRecord;
import com.ape.springboot_01_marathon.pojo.dto.RegistrationQuery;
import com.ape.springboot_01_marathon.pojo.vo.RankVO;
import com.ape.springboot_01_marathon.pojo.vo.ResultVO;

import java.util.List;
import java.util.Map;

public interface ResultService {

    Long input(ResultRecord record);

    boolean update(ResultRecord record);

    PageResult<ResultVO> page(RegistrationQuery query);

    /** 赛事总排名，分页 */
    PageResult<RankVO> eventRanking(Long eventId, Integer pageNum, Integer pageSize);

    /** 男女分组排名，不分页，返回 maleList / femaleList */
    Map<String, List<RankVO>> genderRanking(Long eventId);

    /** 年龄分组排名，不分页 */
    Map<String, List<RankVO>> ageRanking(Long eventId);
}


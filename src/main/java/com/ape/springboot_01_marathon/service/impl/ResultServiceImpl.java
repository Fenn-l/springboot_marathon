package com.ape.springboot_01_marathon.service.impl;

import com.ape.springboot_01_marathon.common.BusinessException;
import com.ape.springboot_01_marathon.common.EventStatus;
import com.ape.springboot_01_marathon.common.MyUtil;
import com.ape.springboot_01_marathon.common.PageResult;
import com.ape.springboot_01_marathon.dao.EventMapper;
import com.ape.springboot_01_marathon.dao.RegistrationMapper;
import com.ape.springboot_01_marathon.dao.ResultMapper;
import com.ape.springboot_01_marathon.pojo.MarathonEvent;
import com.ape.springboot_01_marathon.pojo.Registration;
import com.ape.springboot_01_marathon.pojo.ResultRecord;
import com.ape.springboot_01_marathon.pojo.dto.RegistrationQuery;
import com.ape.springboot_01_marathon.pojo.vo.RankVO;
import com.ape.springboot_01_marathon.pojo.vo.ResultVO;
import com.ape.springboot_01_marathon.service.ResultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResultServiceImpl implements ResultService {

    private final ResultMapper resultMapper;
    private final RegistrationMapper registrationMapper;
    private final EventMapper eventMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long input(ResultRecord record) {
        if (record.getRegistrationId() == null) {
            throw new BusinessException("报名记录ID不能为空");
        }
        checkTimes(record);

        // 报名记录必须存在且有效
        Registration reg = registrationMapper.selectById(record.getRegistrationId());
        if (reg == null) {
            throw new BusinessException(404, "报名记录不存在");
        }
        if (reg.getStatus() != 1) {
            throw new BusinessException("该报名记录已取消或退赛，无法录入成绩");
        }

        // 赛事状态门禁：必须「比赛中」才允许录入成绩
        assertEventCanEnterResult(reg.getEventId());

        // 一条报名只能有一条成绩
        if (resultMapper.existsByRegistrationId(record.getRegistrationId()) > 0) {
            throw new BusinessException("该报名记录已录入过成绩，不允许重复录入");
        }

        resultMapper.insert(record);
        log.info("录入成绩 id={} registrationId={} netTime={}",
                record.getId(), record.getRegistrationId(), record.getNetTime());
        return record.getId();
    }

    @Override
    public boolean update(ResultRecord record) {
        if (record.getId() == null) {
            throw new BusinessException("成绩ID不能为空");
        }
        ResultRecord existing = resultMapper.selectById(record.getId());
        if (existing == null) {
            throw new BusinessException(404, "成绩记录不存在");
        }
        checkTimes(record);

        // 赛事状态门禁：修改成绩同样受赛事状态约束
        assertEventCanEnterResultByRegistrationId(existing.getRegistrationId());

        return resultMapper.update(record) > 0;
    }

    /**
     * 成绩录入/修改的赛事状态门禁。
     * <p>
     * 之前完全没有这层校验 —— 赛事还在报名中就能录成绩，
     * 导致「赛事状态」和「有没有成绩」两套数据互不约束
     * （实测库里就有赛事 status=0报名中 却已有 3 条成绩的脏数据）。
     */
    private void assertEventCanEnterResult(Long eventId) {
        MarathonEvent event = eventMapper.selectById(eventId);
        if (event == null) {
            throw new BusinessException(404, "赛事不存在");
        }
        EventStatus.assertCanEnterResult(event.getStatus());
    }

    /** 按报名记录反查赛事，再走状态门禁 */
    private void assertEventCanEnterResultByRegistrationId(Long registrationId) {
        if (registrationId == null) {
            throw new BusinessException("成绩缺少报名记录关联，无法校验赛事状态");
        }
        Registration reg = registrationMapper.selectById(registrationId);
        if (reg == null) {
            throw new BusinessException(404, "报名记录不存在");
        }
        assertEventCanEnterResult(reg.getEventId());
    }

    @Override
    public PageResult<ResultVO> page(RegistrationQuery query) {
        if (query == null) {
            query = new RegistrationQuery();
        }
        // 必须调 normalize()：offset 在这里算好，XML 里写 LIMIT #{pageSize} OFFSET #{offset}。
        // 之前这里只兜底了 pageNum / pageSize，offset 一直是默认值 0，
        // 导致翻到第 2 页返回的还是第 1 页的数据。
        query.normalize();

        List<ResultVO> list = resultMapper.selectPage(query);
        if (list.isEmpty()) {
            return PageResult.empty(query.getPageNum(), query.getPageSize());
        }
        long total = resultMapper.countPage(query);
        return PageResult.of(list, total, query.getPageNum(), query.getPageSize());
    }

    @Override
    public PageResult<RankVO> eventRanking(Long eventId, Integer pageNum, Integer pageSize) {
        if (eventId == null) {
            throw new BusinessException("赛事ID不能为空");
        }
        int pn = (pageNum == null || pageNum < 1) ? 1 : pageNum;
        int ps = (pageSize == null || pageSize < 1) ? 10 : pageSize;
        if (ps > 100) {
            ps = 100;
        }

        // 一次取出全部完赛选手，Java 层算名次后再分页
        List<RankVO> all = resultMapper.selectRanking(eventId);
        if (all.isEmpty()) {
            return PageResult.empty(pn, ps);
        }

        fillRank(all);
        fillNetTimeStr(all);

        int from = (pn - 1) * ps;
        if (from >= all.size()) {
            return PageResult.empty(pn, ps);
        }
        int to = Math.min(from + ps, all.size());
        List<RankVO> pageList = all.subList(from, to);

        return PageResult.of(pageList, (long) all.size(), pn, ps);
    }

    @Override
    public Map<String, List<RankVO>> genderRanking(Long eventId) {
        if (eventId == null) {
            throw new BusinessException("赛事ID不能为空");
        }

        List<RankVO> male = resultMapper.selectRankingByGender(eventId, 1);
        List<RankVO> female = resultMapper.selectRankingByGender(eventId, 2);

        fillRank(male);
        fillRank(female);
        fillNetTimeStr(male);
        fillNetTimeStr(female);

        Map<String, List<RankVO>> result = new HashMap<>(2);
        result.put("maleList", male);
        result.put("femaleList", female);
        return result;
    }

    @Override
    public Map<String, List<RankVO>> ageRanking(Long eventId) {
        if (eventId == null) {
            throw new BusinessException("赛事ID不能为空");
        }

        List<RankVO> all = resultMapper.selectRankingByAgeGroup(eventId);

        // 四个年龄段桶
        List<RankVO> below29 = new ArrayList<>();
        List<RankVO> g30to39 = new ArrayList<>();
        List<RankVO> g40to49 = new ArrayList<>();
        List<RankVO> g50up = new ArrayList<>();

        for (RankVO vo : all) {
            int age = vo.getAge() == null ? 0 : vo.getAge();
            if (age <= 29) {
                below29.add(vo);
            } else if (age <= 39) {
                g30to39.add(vo);
            } else if (age <= 49) {
                g40to49.add(vo);
            } else {
                g50up.add(vo);
            }
        }

        // 每组内部独立重新排名
        for (List<RankVO> group : List.of(below29, g30to39, g40to49, g50up)) {
            fillRank(group);
            fillNetTimeStr(group);
        }

        Map<String, List<RankVO>> result = new HashMap<>(4);
        result.put("group_29below", below29);
        result.put("group_30_39", g30to39);
        result.put("group_40_49", g40to49);
        result.put("group_50up", g50up);
        return result;
    }

    // ==================== 私有方法 ====================

    /** 名次 = 列表下标 + 1 */
    private void fillRank(List<RankVO> list) {
        for (int i = 0; i < list.size(); i++) {
            list.get(i).setRank(i + 1);
        }
    }

    /** 统一把秒数格式化成 HH:mm:ss */
    private void fillNetTimeStr(List<RankVO> list) {
        for (RankVO vo : list) {
            vo.setNetTimeStr(MyUtil.formatSeconds(vo.getNetTime()));
        }
    }

    private void checkTimes(ResultRecord record) {
        if (record.getNetTime() == null || record.getNetTime() <= 0) {
            throw new BusinessException("净成绩必须大于 0");
        }
        if (record.getChipTime() != null
                && record.getChipTime() > 0
                && record.getChipTime() < record.getNetTime()) {
            throw new BusinessException("枪声成绩不能小于净成绩");
        }
        if (record.getFinishStatus() == null
                || record.getFinishStatus() < 1 || record.getFinishStatus() > 3) {
            throw new BusinessException("完赛状态只能是 1完赛 2未完赛 3退赛");
        }
    }
}

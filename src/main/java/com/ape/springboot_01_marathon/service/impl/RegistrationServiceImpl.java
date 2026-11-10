package com.ape.springboot_01_marathon.service.impl;

import com.ape.springboot_01_marathon.common.BusinessException;
import com.ape.springboot_01_marathon.common.EventStatus;
import com.ape.springboot_01_marathon.common.MyUtil;
import com.ape.springboot_01_marathon.common.PageResult;
import com.ape.springboot_01_marathon.dao.EventMapper;
import com.ape.springboot_01_marathon.dao.RegistrationMapper;
import com.ape.springboot_01_marathon.dao.RunnerMapper;
import com.ape.springboot_01_marathon.pojo.MarathonEvent;
import com.ape.springboot_01_marathon.pojo.Registration;
import com.ape.springboot_01_marathon.pojo.Runner;
import com.ape.springboot_01_marathon.pojo.dto.RegistrationQuery;
import com.ape.springboot_01_marathon.pojo.vo.RegistrationVO;
import com.ape.springboot_01_marathon.service.RegistrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationServiceImpl implements RegistrationService {

    private final RegistrationMapper registrationMapper;
    private final EventMapper eventMapper;
    private final RunnerMapper runnerMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submit(Registration registration) {
        if (registration.getEventId() == null || registration.getRunnerId() == null) {
            throw new BusinessException("赛事ID和选手ID都不能为空");
        }

        // 1) 赛事存在性与状态校验（只有「报名中」能报名）
        MarathonEvent event = eventMapper.selectById(registration.getEventId());
        if (event == null) {
            throw new BusinessException(404, "赛事不存在");
        }
        EventStatus.assertCanRegister(event.getStatus());

        // 2) 选手存在性校验
        if (runnerMapper.selectById(registration.getRunnerId()) == null) {
            throw new BusinessException(404, "选手不存在");
        }

        // 3) 防重复报名：应用层预检，给出友好提示
        if (registrationMapper.existsByEventAndRunner(
                registration.getEventId(), registration.getRunnerId()) > 0) {
            throw new BusinessException("该选手已报名过此赛事，请勿重复报名");
        }

        // 4) 名额校验（先看余量，给出更友好的提示）
        int remain = event.getQuota() - event.getRegisteredCount();
        if (remain <= 0) {
            throw new BusinessException("该赛事名额已满");
        }

        // 5) 落库
        try {
            registration.setPayStatus(0);
            registration.setStatus(1);
            registrationMapper.insert(registration);
        } catch (DuplicateKeyException e) {
            // 并发下同一选手同时提交，靠唯一索引 uk_event_runner 兜底
            throw new BusinessException("该选手已报名过此赛事，请勿重复报名");
        }

        // 6) 报名人数 +1，SQL 里带 registered_count < quota 条件做并发兜底
        int rows = eventMapper.increaseRegisteredCount(event.getId());
        if (rows == 0) {
            // 名额在并发下被抢完，抛异常触发事务回滚，报名记录也会撤销
            throw new BusinessException("报名失败，名额已满");
        }

        log.info("选手 {} 报名赛事 {} 成功，报名记录 id={}",
                registration.getRunnerId(), event.getId(), registration.getId());
        return registration.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancel(Long id) {
        Registration reg = registrationMapper.selectById(id);
        if (reg == null) {
            throw new BusinessException(404, "报名记录不存在");
        }
        if (reg.getStatus() != 1) {
            throw new BusinessException("只有已报名的记录可以取消");
        }

        // 已录入成绩的不允许取消。成绩是赛事的最终结果，改了会影响排名和统计。
        if (registrationMapper.existsResult(id) > 0) {
            throw new BusinessException("该记录已录入成绩，不允许取消。如需修正请修改成绩");
        }

        // <b>物理删除</b>而不是标记 status=2。
        // 原因：registration.runner_id 上有外键，外键只看行是否存在、不看 status。
        // 留着一条 status=2 的行，选手档案就永远删不掉（数据库 1451）。
        // 业务语义上「取消报名」本来就等于「没报过这个赛事」，物理删除更干净：
        // 号码布、支付状态一并消失，不需要单独清 bib_number。
        int rows = registrationMapper.cancel(id);
        if (rows == 0) {
            throw new BusinessException("取消失败，记录状态已变更");
        }

        // 报名人数 -1
        eventMapper.decreaseRegisteredCount(reg.getEventId());
        log.info("报名记录 {} 已物理删除（原 bib={}），赛事 {} 报名人数 -1",
                id, reg.getBibNumber(), reg.getEventId());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String assignBibNumber(Long id, String bibNumber) {
        if (bibNumber == null || bibNumber.isBlank()) {
            throw new BusinessException("号码布编号不能为空");
        }
        Registration reg = registrationMapper.selectById(id);
        if (reg == null) {
            throw new BusinessException(404, "报名记录不存在");
        }
        if (reg.getStatus() != 1) {
            throw new BusinessException("只有有效报名记录可以分配号码布");
        }

        int rows = registrationMapper.assignBibNumber(id, bibNumber);
        if (rows == 0) {
            throw new BusinessException("分配失败，记录状态已变更");
        }

        log.info("报名记录 {} 分配号码布 {}", id, bibNumber);
        return bibNumber;
    }

    @Override
    public String suggestBib(Long id) {
        Registration reg = registrationMapper.selectById(id);
        if (reg == null) {
            throw new BusinessException(404, "报名记录不存在");
        }
        if (reg.getStatus() != 1) {
            throw new BusinessException("只有有效报名记录可以分配号码布");
        }
        if (reg.getBibNumber() != null && !reg.getBibNumber().isBlank()) {
            return reg.getBibNumber();
        }
        MarathonEvent event = eventMapper.selectById(reg.getEventId());
        if (event == null) {
            throw new BusinessException(404, "赛事不存在");
        }
        Double km = event.getDistanceKm() == null ? null : event.getDistanceKm().doubleValue();
        String prefix = MyUtil.bibPrefix(km);
        Integer max = registrationMapper.maxBibSeq(event.getId(), prefix);
        int next = (max == null ? 0 : max) + 1;
        return MyUtil.buildBibNumber(prefix, next);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean markPaid(Long id) {
        Registration reg = registrationMapper.selectById(id);
        if (reg == null) {
            throw new BusinessException(404, "报名记录不存在");
        }
        if (reg.getStatus() != 1) {
            throw new BusinessException("只有有效报名记录可以确认支付");
        }
        if (reg.getPayStatus() != null && reg.getPayStatus() == 1) {
            throw new BusinessException("该报名已经支付");
        }
        int rows = registrationMapper.markPaid(id);
        if (rows == 0) {
            throw new BusinessException("确认支付失败，记录状态已变更");
        }
        log.info("报名记录 {} 已确认支付", id);
        return true;
    }

    /**
     * 清空号码布。
     * <p>
     * 这是「取消报名」的前置步骤之一，但也可以独立使用（比如号码布印错了要重编）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean clearBibNumber(Long id) {
        Registration reg = registrationMapper.selectById(id);
        if (reg == null) {
            throw new BusinessException(404, "报名记录不存在");
        }
        if (reg.getStatus() != 1) {
            throw new BusinessException("只有有效报名记录可以清空号码布");
        }
        if (reg.getBibNumber() == null || reg.getBibNumber().isBlank()) {
            throw new BusinessException("该记录本来就没有号码布，无需清空");
        }
        // 已录入成绩的不允许清号码布，否则排名页会显示空号码
        if (registrationMapper.existsResult(id) > 0) {
            throw new BusinessException("该记录已录入成绩，号码布与成绩关联，不能清空");
        }

        int rows = registrationMapper.clearBibNumber(id);
        if (rows == 0) {
            throw new BusinessException("清空失败，记录状态已变更");
        }

        log.info("报名记录 {} 的号码布 {} 已清空", id, reg.getBibNumber());
        return true;
    }

    @Override
    public PageResult<RegistrationVO> page(RegistrationQuery query) {
        normalizePage(query);

        List<RegistrationVO> list = registrationMapper.selectPage(query);
        if (list.isEmpty()) {
            return PageResult.empty(query.getPageNum(), query.getPageSize());
        }
        long total = registrationMapper.countPage(query);
        return PageResult.of(list, total, query.getPageNum(), query.getPageSize());
    }

    @Override
    public RegistrationVO detail(Long id) {
        RegistrationVO vo = registrationMapper.selectDetailById(id);
        if (vo == null) {
            throw new BusinessException(404, "报名记录不存在");
        }
        return vo;
    }

    private void normalizePage(RegistrationQuery query) {
        query.normalize();
        query.trim();
    }
}


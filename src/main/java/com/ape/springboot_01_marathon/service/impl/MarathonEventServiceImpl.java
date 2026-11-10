package com.ape.springboot_01_marathon.service.impl;

import com.ape.springboot_01_marathon.common.BusinessException;
import com.ape.springboot_01_marathon.common.EventStatus;
import com.ape.springboot_01_marathon.common.PageResult;
import com.ape.springboot_01_marathon.dao.EventMapper;
import com.ape.springboot_01_marathon.dao.RegistrationMapper;
import com.ape.springboot_01_marathon.pojo.MarathonEvent;
import com.ape.springboot_01_marathon.pojo.dto.EventQuery;
import com.ape.springboot_01_marathon.pojo.vo.EventVO;
import com.ape.springboot_01_marathon.service.MarathonEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarathonEventServiceImpl implements MarathonEventService {

    private final EventMapper eventMapper;
    private final RegistrationMapper registrationMapper;

    @Override
    public Long add(MarathonEvent event) {
        checkEvent(event, false);
        // 新建赛事已报名人数固定为 0，不接受前端传值
        event.setRegisteredCount(0);
        eventMapper.insert(event);
        log.info("新增赛事 id={} name={}", event.getId(), event.getEventName());
        return event.getId();
    }

    @Override
    public boolean update(MarathonEvent event) {
        if (event.getId() == null) {
            throw new BusinessException("赛事ID不能为空");
        }
        MarathonEvent db = requireEvent(event.getId());

        // 已有人报名时不允许把名额改到低于已报名数
        if (event.getQuota() != null && event.getQuota() < db.getRegisteredCount()) {
            throw new BusinessException("总名额不能小于已报名人数 " + db.getRegisteredCount());
        }
        // 赛事已结束不允许再改
        if (db.getStatus() != null && db.getStatus() == EventStatus.FINISHED) {
            throw new BusinessException("赛事已结束，不允许修改");
        }

        // 状态流转校验：只允许相邻推进，不允许跨档或回退
        if (event.getStatus() != null) {
            EventStatus.assertTransition(db.getStatus(), event.getStatus());
        }

        checkEvent(event, true);
        return eventMapper.update(event) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(Long id) {
        requireEvent(id);

        // 取消报名已经物理删除记录，所以这里只查有效报名（status=1），
        // 口径与 RunnerServiceImpl.delete 一致。
        int regCount = eventMapper.countActiveRegistration(id);
        if (regCount > 0) {
            throw new BusinessException(
                    "该赛事还有 " + regCount + " 条有效报名，无法删除。请先到「报名管理」中取消这些报名");
        }

        // 赛事侧同样兜底清理历史残留（无成绩的报名行）
        int orphans = registrationMapper.countAllByEvent(id);
        if (orphans > 0) {
            log.warn("删除赛事 {} 前清理 {} 条无成绩的历史报名记录", id, orphans);
            registrationMapper.deleteOrphanRegistrationsByEvent(id);
        }

        return eventMapper.deleteById(id) > 0;
    }

    @Override
    public PageResult<EventVO> page(EventQuery query) {
        normalizePage(query);

        List<EventVO> list = eventMapper.selectPage(query);
        if (list.isEmpty()) {
            return PageResult.empty(query.getPageNum(), query.getPageSize());
        }
        long total = eventMapper.countPage(query);
        return PageResult.of(list, total, query.getPageNum(), query.getPageSize());
    }

    @Override
    public EventVO detail(Long id) {
        EventVO vo = eventMapper.selectDetailById(id);
        if (vo == null) {
            throw new BusinessException(404, "赛事不存在");
        }
        return vo;
    }

    // ==================== 私有方法 ====================

    /** 取赛事，不存在直接抛异常 */
    private MarathonEvent requireEvent(Long id) {
        MarathonEvent event = eventMapper.selectById(id);
        if (event == null) {
            throw new BusinessException(404, "赛事不存在");
        }
        return event;
    }

    /** 新增/修改的公共字段校验 */
    private void checkEvent(MarathonEvent event, boolean isUpdate) {
        // ⚠️ SQL 是动态的（只 update 传了的字段），所以更新时不能强校验没传的字段。
        // 原来这里无论新增还是更新都要求 eventName 非空，
        // 结果「只改状态」这种局部更新会被误判成「赛事名称不能为空」。
        // 现在：必填项只在新增时校验，更新时只校验「传了但格式不对」的情况。
        if (!isUpdate) {
            if (event.getEventName() == null || event.getEventName().isBlank()) {
                throw new BusinessException("赛事名称不能为空");
            }
            if (event.getEventType() == null
                    || event.getEventType() < 1 || event.getEventType() > 3) {
                throw new BusinessException("赛事类型只能是 1全程 2半程 3迷你跑");
            }
            if (event.getEventDate() == null) {
                throw new BusinessException("比赛日期不能为空");
            }
            if (event.getStartLocation() == null || event.getStartLocation().isBlank()) {
                throw new BusinessException("起点地点不能为空");
            }
        } else {
            // 更新时字段传了就必须合法
            if (event.getEventName() != null && event.getEventName().isBlank()) {
                throw new BusinessException("赛事名称不能为空");
            }
            if (event.getEventType() != null
                    && (event.getEventType() < 1 || event.getEventType() > 3)) {
                throw new BusinessException("赛事类型只能是 1全程 2半程 3迷你跑");
            }
            if (event.getStartLocation() != null && event.getStartLocation().isBlank()) {
                throw new BusinessException("起点地点不能为空");
            }
        }
        if (event.getQuota() != null && event.getQuota() <= 0) {
            throw new BusinessException("总名额必须大于 0");
        }
        // ⚠️ 原来写的是 status < 0 && status > 2，条件永远为 false，等于完全没校验。
        // 现在统一交给 EventStatus 收口。
        if (event.getStatus() != null && !EventStatus.isValid(event.getStatus())) {
            throw new BusinessException("状态只能是 0报名中 1报名截止 2比赛中 3已结束");
        }
    }

    /** 分页参数兜底 + 计算 offset，XML 里直接用 #{offset} */
    private void normalizePage(EventQuery query) {
        query.normalize();
        query.trim();
    }
}


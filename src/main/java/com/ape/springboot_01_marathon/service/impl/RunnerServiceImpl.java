package com.ape.springboot_01_marathon.service.impl;

import com.ape.springboot_01_marathon.common.BusinessException;
import com.ape.springboot_01_marathon.common.PageResult;
import com.ape.springboot_01_marathon.dao.RegistrationMapper;
import com.ape.springboot_01_marathon.dao.RunnerMapper;
import com.ape.springboot_01_marathon.pojo.Runner;
import com.ape.springboot_01_marathon.pojo.dto.RunnerQuery;
import com.ape.springboot_01_marathon.service.RunnerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RunnerServiceImpl implements RunnerService {

    private final RunnerMapper runnerMapper;
    private final RegistrationMapper registrationMapper;

    @Override
    public Long add(Runner runner) {
        checkRunner(runner, false);
        // 身份证唯一索引兜底：先查一次给出友好提示
        if (runnerMapper.existsIdCard(runner.getIdCard(), null) > 0) {
            throw new BusinessException("身份证号已存在，请勿重复录入");
        }
        runnerMapper.insert(runner);
        log.info("新增选手 id={} name={}", runner.getId(), runner.getName());
        return runner.getId();
    }

    @Override
    public boolean update(Runner runner) {
        if (runner.getId() == null) {
            throw new BusinessException("选手ID不能为空");
        }
        requireRunner(runner.getId());
        checkRunner(runner, true);

        // 改身份证时排除自己再查唯一性
        if (runner.getIdCard() != null
                && runnerMapper.existsIdCard(runner.getIdCard(), runner.getId()) > 0) {
            throw new BusinessException("身份证号已被其他选手使用");
        }
        return runnerMapper.update(runner) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(Long id) {
        requireRunner(id);

        // 只拦「有效报名」（status=1）。取消报名已经物理删除记录，所以这里查到的就是全部了。
        //
        // 之前踩过的坑：这里曾写成「查全部报名记录」，结果取消报名的选手仍然删不掉——
        // 因为当时 cancel 只是 UPDATE status=2，行还在表里，外键 1451 照样拦。
        // 改成 cancel 物理删除后，这里就只查 status=1 了，两边口径一致。
        int regCount = runnerMapper.countActiveRegistration(id);
        if (regCount > 0) {
            throw new BusinessException(
                    "该选手还有 " + regCount + " 条有效报名，无法删除。请先到「报名管理」中取消报名");
        }

        // 兜底：若库里还残留没有成绩的历史报名行（比如老数据），
        // 自动清理掉，否则数据库会抛 1451，用户看到"系统繁忙"。
        int orphans = registrationMapper.countAllByRunner(id);
        if (orphans > 0) {
            log.warn("删除选手 {} 前清理 {} 条无成绩的历史报名记录", id, orphans);
            registrationMapper.deleteOrphanRegistrations(id);
        }

        return runnerMapper.deleteById(id) > 0;
    }

    @Override
    public PageResult<Runner> page(RunnerQuery query) {
        normalizePage(query);

        List<Runner> list = runnerMapper.selectPage(query);
        if (list.isEmpty()) {
            return PageResult.empty(query.getPageNum(), query.getPageSize());
        }
        long total = runnerMapper.countPage(query);
        return PageResult.of(list, total, query.getPageNum(), query.getPageSize());
    }

    @Override
    public Runner detail(Long id) {
        return requireRunner(id);
    }

    // ==================== 私有方法 ====================

    private Runner requireRunner(Long id) {
        Runner runner = runnerMapper.selectById(id);
        if (runner == null) {
            throw new BusinessException(404, "选手不存在");
        }
        return runner;
    }

    private void checkRunner(Runner runner, boolean isUpdate) {
        if (runner.getName() == null || runner.getName().isBlank()) {
            throw new BusinessException("姓名不能为空");
        }
        if (runner.getGender() == null || (runner.getGender() != 1 && runner.getGender() != 2)) {
            throw new BusinessException("性别只能是 1男 2女");
        }
        if (runner.getIdCard() == null
                || !runner.getIdCard().matches("^\\d{17}[\\dXx]$")) {
            throw new BusinessException("身份证号格式不正确");
        }
        if (runner.getPhone() == null || !runner.getPhone().matches("^1[3-9]\\d{9}$")) {
            throw new BusinessException("手机号格式不正确");
        }
        if (runner.getEmergencyContact() == null || runner.getEmergencyContact().isBlank()) {
            throw new BusinessException("紧急联系人不能为空");
        }
        if (!isUpdate) {
            LocalDate birth = runner.getBirthDate();
            if (birth == null) {
                throw new BusinessException("出生日期不能为空");
            }
            if (birth.isAfter(LocalDate.now())) {
                throw new BusinessException("出生日期不能晚于今天");
            }
            // 全马最低年龄 16 岁，这里按赛事通用规则校验
            int age = Period.between(birth, LocalDate.now()).getYears();
            if (age < 16) {
                throw new BusinessException("参赛年龄不得低于 16 周岁，当前 " + age + " 岁");
            }
            if (age > 100) {
                throw new BusinessException("出生日期有误");
            }
        }
    }

    private void normalizePage(RunnerQuery query) {
        query.normalize();
        query.trim();
    }
}


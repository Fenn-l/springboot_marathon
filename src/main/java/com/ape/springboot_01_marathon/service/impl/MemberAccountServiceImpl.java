package com.ape.springboot_01_marathon.service.impl;

import com.ape.springboot_01_marathon.common.BusinessException;
import com.ape.springboot_01_marathon.dao.MemberAccountMapper;
import com.ape.springboot_01_marathon.dao.RunnerMapper;
import com.ape.springboot_01_marathon.pojo.MemberAccount;
import com.ape.springboot_01_marathon.pojo.Runner;
import com.ape.springboot_01_marathon.service.MemberAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * B 端：选手登录账号管理实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberAccountServiceImpl implements MemberAccountService {

    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final int MAX_PASSWORD_LENGTH = 30;

    private final MemberAccountMapper memberAccountMapper;
    private final RunnerMapper runnerMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public Map<String, Object> status(Long runnerId) {
        Runner runner = requireRunner(runnerId);
        MemberAccount account = memberAccountMapper.selectByRunnerId(runnerId);

        Map<String, Object> data = new HashMap<>(4);
        data.put("exists", account != null);
        // 登录手机号：已开通就是账号上的，没开通就用档案里的
        data.put("phone", account != null ? account.getPhone() : runner.getPhone());
        data.put("runnerName", runner.getName());
        data.put("runnerId", runnerId);
        return data;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> resetPassword(Long runnerId, String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new BusinessException("密码至少 " + MIN_PASSWORD_LENGTH + " 位");
        }
        if (password.length() > MAX_PASSWORD_LENGTH) {
            throw new BusinessException("密码不能超过 " + MAX_PASSWORD_LENGTH + " 位");
        }

        Runner runner = requireRunner(runnerId);
        MemberAccount existing = memberAccountMapper.selectByRunnerId(runnerId);
        String encoded = passwordEncoder.encode(password);

        boolean created;
        if (existing == null) {
            // 开通新账号：登录手机号取档案里的手机号
            MemberAccount account = new MemberAccount();
            account.setPhone(runner.getPhone());
            account.setPassword(encoded);
            account.setRunnerId(runnerId);
            try {
                memberAccountMapper.insert(account);
            } catch (DuplicateKeyException e) {
                // 并发下另一个管理员同时开通了，退化成重置
                memberAccountMapper.updatePassword(runnerId, encoded);
                created = false;
                log.info("管理员为选手 {} 重置密码（并发开通，转为重置）", runnerId);
                return buildResult(false, runner.getPhone());
            }
            created = true;
            log.info("管理员为选手 {} 开通 C 端账号 phone={}", runnerId, runner.getPhone());
        } else {
            // 已有账号，只重置密码，登录手机号不动
            memberAccountMapper.updatePassword(runnerId, encoded);
            created = false;
            log.info("管理员为选手 {} 重置了 C 端登录密码", runnerId);
        }

        return buildResult(created, existing != null ? existing.getPhone() : runner.getPhone());
    }

    private Map<String, Object> buildResult(boolean created, String phone) {
        Map<String, Object> data = new HashMap<>(3);
        data.put("created", created);
        data.put("phone", phone);
        return data;
    }

    /** 取选手档案，不存在直接抛异常 */
    private Runner requireRunner(Long runnerId) {
        if (runnerId == null) {
            throw new BusinessException("选手ID不能为空");
        }
        Runner runner = runnerMapper.selectById(runnerId);
        if (runner == null) {
            throw new BusinessException(404, "选手不存在");
        }
        return runner;
    }
}

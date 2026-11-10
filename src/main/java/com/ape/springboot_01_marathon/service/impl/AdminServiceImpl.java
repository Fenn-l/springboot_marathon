package com.ape.springboot_01_marathon.service.impl;

import com.ape.springboot_01_marathon.common.BusinessException;
import com.ape.springboot_01_marathon.common.ErrorCode;
import com.ape.springboot_01_marathon.common.TokenManager;
import com.ape.springboot_01_marathon.dao.AdminMapper;
import com.ape.springboot_01_marathon.pojo.Admin;
import com.ape.springboot_01_marathon.pojo.dto.LoginDTO;
import com.ape.springboot_01_marathon.pojo.vo.LoginVO;
import com.ape.springboot_01_marathon.service.AdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final AdminMapper adminMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenManager tokenManager;

    @Override
    public LoginVO login(LoginDTO dto) {
        // 1. 按账号查
        Admin admin = adminMapper.selectByUsername(dto.getUsername());

        // 2. 账号不存在和密码错误返回同一个提示。
        //    这是安全考虑：如果分开提示，攻击者能据此判断哪些账号真实存在。
        if (admin == null) {
            log.warn("登录失败：账号 {} 不存在", dto.getUsername());
            throw new BusinessException(ErrorCode.LOGIN_FAILED, "账号或密码错误");
        }

        // 3. BCrypt 校验密码。
        //    matches() 会从密文里取出盐和成本因子，重新算一遍再比对。
        //    数据库里存的是 BCrypt 密文，不是明文。
        if (!passwordEncoder.matches(dto.getPassword(), admin.getPassword())) {
            log.warn("登录失败：账号 {} 密码不正确", dto.getUsername());
            throw new BusinessException(ErrorCode.LOGIN_FAILED, "账号或密码错误");
        }

        // 4. 发 Token
        String token = tokenManager.create(admin.getId());

        // 5. 组装返回
        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setAdminId(admin.getId());
        vo.setUsername(admin.getUsername());
        vo.setAdminName(admin.getAdminName());
        vo.setLoginTime(LocalDateTime.now());
        return vo;
    }

    @Override
    public void logout(String token) {
        tokenManager.remove(token);
    }
}
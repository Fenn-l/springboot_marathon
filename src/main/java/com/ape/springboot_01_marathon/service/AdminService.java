package com.ape.springboot_01_marathon.service;

import com.ape.springboot_01_marathon.pojo.dto.LoginDTO;
import com.ape.springboot_01_marathon.pojo.vo.LoginVO;

public interface AdminService {

    /** 登录，返回 Token 和管理员信息 */
    LoginVO login(LoginDTO dto);

    /** 退出登录，注销 Token */
    void logout(String token);
}
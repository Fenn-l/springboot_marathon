package com.ape.springboot_01_marathon.controller;

import com.ape.springboot_01_marathon.common.Result;
import com.ape.springboot_01_marathon.pojo.dto.LoginDTO;
import com.ape.springboot_01_marathon.pojo.vo.LoginVO;
import com.ape.springboot_01_marathon.service.AdminService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    /**
     * 登录。这个接口在拦截器白名单里，不需要 Token。
     *
     * <p>账号 admin，密码 123456
     */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.success("登录成功", adminService.login(dto));
    }

    /**
     * 退出登录
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        adminService.logout(token);
        return Result.success("已退出登录", null);
    }

    /**
     * 查询当前登录人信息，用于前端刷新页面后恢复状态
     */
    @GetMapping("/info")
    public Result<Map<String, Object>> info(HttpServletRequest request) {
        Object adminId = request.getAttribute("adminId");
        Map<String, Object> data = new HashMap<>();
        data.put("adminId", adminId);
        return Result.success(data);
    }
}
package com.ape.springboot_01_marathon.controller;

import com.ape.springboot_01_marathon.common.Result;
import com.ape.springboot_01_marathon.pojo.Runner;
import com.ape.springboot_01_marathon.pojo.dto.MemberChangePasswordDTO;
import com.ape.springboot_01_marathon.pojo.dto.MemberLoginDTO;
import com.ape.springboot_01_marathon.pojo.dto.MemberRegisterDTO;
import com.ape.springboot_01_marathon.pojo.vo.MyRegistrationVO;
import com.ape.springboot_01_marathon.pojo.vo.MyResultVO;
import com.ape.springboot_01_marathon.service.MemberService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * C 端选手接口（需登录）。
 * <p>
 * 全部走 {@code /api/member/**}，由 {@code MemberAuthInterceptor} 校验
 * scope=member 的 Token；选手 id 一律从 request 里取，<b>不接受前端传</b>，
 * 防止越权操作他人数据。
 */
@RestController
@RequestMapping("/api/member")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    // ==================== 账号 ====================

    /** 注册 */
    @PostMapping("/register")
    public Result<Map<String, Object>> register(@Valid @RequestBody MemberRegisterDTO dto) {
        return Result.success("注册成功", memberService.register(dto));
    }

    /** 登录 */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody MemberLoginDTO dto) {
        return Result.success("登录成功", memberService.login(dto.getPhone(), dto.getPassword()));
    }

    /** 退出登录 */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        memberService.logout(extractToken(request));
        return Result.success("已退出登录", null);
    }

    /** 修改登录密码 */
    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody MemberChangePasswordDTO dto,
                                       HttpServletRequest request) {
        memberService.changePassword(currentRunnerId(request),
                dto.getOldPassword(), dto.getNewPassword());
        return Result.success("密码已修改", null);
    }

    // ==================== 资料 ====================

    /** 我的资料 */
    @GetMapping("/profile")
    public Result<Runner> profile(HttpServletRequest request) {
        return Result.success(memberService.profile(currentRunnerId(request)));
    }

    /** 修改我的资料 */
    @PutMapping("/profile")
    public Result<Void> updateProfile(@RequestBody Runner runner, HttpServletRequest request) {
        boolean ok = memberService.updateProfile(currentRunnerId(request), runner);
        return ok ? Result.success("已保存", null) : Result.error("保存失败");
    }

    // ==================== 报名 ====================

    /** 我的报名列表 */
    @GetMapping("/registrations")
    public Result<List<MyRegistrationVO>> myRegistrations(HttpServletRequest request) {
        return Result.success(memberService.myRegistrations(currentRunnerId(request)));
    }

    /** 我要报名 */
    @PostMapping("/events/{eventId}/register")
    public Result<Map<String, Object>> registerEvent(@PathVariable Long eventId,
                                                     HttpServletRequest request) {
        Long id = memberService.registerEvent(currentRunnerId(request), eventId);
        return Result.success("报名成功", Map.of("registrationId", id));
    }

    /** 取消我的报名 */
    @PostMapping("/registrations/{id}/cancel")
    public Result<Void> cancelRegistration(@PathVariable Long id, HttpServletRequest request) {
        boolean ok = memberService.cancelMyRegistration(currentRunnerId(request), id);
        return ok ? Result.success("报名已取消", null) : Result.error("取消失败");
    }

    // ==================== 成绩 ====================

    /** 我的成绩 */
    @GetMapping("/results")
    public Result<List<MyResultVO>> myResults(HttpServletRequest request) {
        return Result.success(memberService.myResults(currentRunnerId(request)));
    }

    /** 我的完赛证书 */
    @GetMapping("/certificates/{registrationId}")
    public Result<MyResultVO> certificate(@PathVariable Long registrationId,
                                          HttpServletRequest request) {
        return Result.success(memberService.certificate(currentRunnerId(request), registrationId));
    }

    // ==================== 私有 ====================

    /** 从 request 里取当前选手 id（由拦截器写入） */
    private Long currentRunnerId(HttpServletRequest request) {
        Object id = request.getAttribute("runnerId");
        if (id == null) {
            throw new com.ape.springboot_01_marathon.common.BusinessException(40100, "请先登录");
        }
        return (Long) id;
    }

    /** 取原始 Token（注销用） */
    private String extractToken(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (token == null || token.isBlank()) {
            token = request.getParameter("token");
        }
        return token;
    }
}

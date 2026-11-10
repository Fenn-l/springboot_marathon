package com.ape.springboot_01_marathon.controller;

import com.ape.springboot_01_marathon.common.Result;
import com.ape.springboot_01_marathon.pojo.dto.ResetPasswordDTO;
import com.ape.springboot_01_marathon.service.MemberAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * B 端：选手登录账号管理。
 * <p>
 * 解决一个真实的业务缺口：
 * <b>管理员在 B 端建的选手档案，选手在 C 端登不进去</b>。
 * <ul>
 *   <li>C 端注册会因为「该身份证已建过档案」被挡掉；</li>
 *   <li>但账号又不存在，登录时手机号查不到账号；</li>
 *   <li>选手只能找组委会手动处理。</li>
 * </ul>
 * 这里给管理员一个正规入口：为已建档的选手开通账号，或重置其密码。
 * <p>
 * 与 {@code /api/member/**}（选手自己改密）分开：
 * 选手改密需要「原密码」，管理员重置不需要 —— 管理员是可信方。
 */
@RestController
@RequestMapping("/api/member-account")
@RequiredArgsConstructor
public class MemberAccountController {

    private final MemberAccountService memberAccountService;

    /** 查询某个选手的账号状态（有没有开通过） */
    @GetMapping("/status/{runnerId}")
    public Result<Map<String, Object>> status(@PathVariable Long runnerId) {
        return Result.success(memberAccountService.status(runnerId));
    }

    /**
     * 开通账号或重置密码。
     * <p>
     * 幂等：账号已存在就是重置密码，不存在就是开通。
     */
    @PostMapping("/reset/{runnerId}")
    public Result<Map<String, Object>> reset(@PathVariable Long runnerId,
                                             @Valid @RequestBody ResetPasswordDTO dto) {
        return Result.success("密码已重置", memberAccountService.resetPassword(runnerId, dto.getPassword()));
    }
}

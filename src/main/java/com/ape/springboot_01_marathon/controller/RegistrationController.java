package com.ape.springboot_01_marathon.controller;

import com.ape.springboot_01_marathon.common.PageResult;
import com.ape.springboot_01_marathon.common.Result;
import com.ape.springboot_01_marathon.pojo.Registration;
import com.ape.springboot_01_marathon.pojo.dto.RegistrationQuery;
import com.ape.springboot_01_marathon.pojo.vo.RegistrationVO;
import com.ape.springboot_01_marathon.service.RegistrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/register")
@RequiredArgsConstructor
public class RegistrationController {

    private final RegistrationService registrationService;

    /** 提交报名 */
    @PostMapping("/submit")
    public Result<Map<String, Object>> submit(@RequestBody Registration registration) {
        Long id = registrationService.submit(registration);
        return Result.success(Map.of("registrationId", id));
    }

    /** 取消报名。号码布会由 Service 自动清空，不需要先手动清 */
    @PostMapping("/cancel")
    public Result<Void> cancel(@RequestParam Long id) {
        return registrationService.cancel(id) ? Result.success("报名已取消，号码布已释放", null)
                : Result.error("取消失败");
    }

    /** 分配号码布 */
    @PutMapping("/bib")
    public Result<String> assignBib(@RequestParam Long id,
                                    @RequestParam String bibNumber) {
        return Result.success(registrationService.assignBibNumber(id, bibNumber));
    }

    /** 建议下一枚号码布：全马 A、半马 B、迷你跑 N */
    @GetMapping("/suggestBib")
    public Result<Map<String, String>> suggestBib(@RequestParam Long id) {
        String bib = registrationService.suggestBib(id);
        return Result.success(Map.of("bibNumber", bib));
    }

    /** 确认支付 */
    @PutMapping("/pay")
    public Result<Void> markPaid(@RequestParam Long id) {
        return registrationService.markPaid(id)
                ? Result.success("已确认支付", null)
                : Result.error("确认支付失败");
    }

    /** 清空号码布，把号码布释放回池子 */
    @DeleteMapping("/bib")
    public Result<Void> clearBib(@RequestParam Long id) {
        return registrationService.clearBibNumber(id)
                ? Result.success("号码布已清空", null)
                : Result.error("清空失败");
    }

    /** 分页查询报名名单 */
    @GetMapping("/list")
    public Result<PageResult<RegistrationVO>> list(@ModelAttribute RegistrationQuery query) {
        return Result.success(registrationService.page(query));
    }

    /** 报名详情 */
    @GetMapping("/detail/{id}")
    public Result<RegistrationVO> detail(@PathVariable Long id) {
        return Result.success(registrationService.detail(id));
    }
}

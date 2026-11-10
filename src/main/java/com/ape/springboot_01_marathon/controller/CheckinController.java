package com.ape.springboot_01_marathon.controller;

import com.ape.springboot_01_marathon.common.Result;
import com.ape.springboot_01_marathon.pojo.vo.MyResultVO;
import com.ape.springboot_01_marathon.service.CheckinService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * B 端现场运营接口：检录核销 + 证书签发。
 * <p>
 * 需要管理员 Token（走默认的 AuthInterceptor）。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CheckinController {

    private final CheckinService checkinService;

    /**
     * 检录核销。
     *
     * @param registrationId 报名记录 id（与 bibNumber 二选一）
     * @param bibNumber      号码布
     */
    @PostMapping("/checkin")
    public Result<Map<String, Object>> checkin(@RequestParam(required = false) Long id,
                                               @RequestParam(required = false) String bibNumber) {
        return Result.success("检录成功", checkinService.checkin(id, bibNumber));
    }

    /** 某赛事的检录进度 */
    @GetMapping("/checkin/progress/{eventId}")
    public Result<Map<String, Object>> progress(@PathVariable Long eventId) {
        return Result.success(checkinService.progress(eventId));
    }

    /** 某赛事可签发证书的名单 */
    @GetMapping("/certificate/list/{eventId}")
    public Result<List<MyResultVO>> certificateList(@PathVariable Long eventId) {
        return Result.success(checkinService.certificateList(eventId));
    }

    /** 批量签发证书 */
    @PostMapping("/certificate/issue")
    public Result<Map<String, Object>> issue(@RequestParam Long eventId) {
        int n = checkinService.issueCertificates(eventId);
        return Result.success("已签发 " + n + " 份证书", Map.of("count", n));
    }
}

package com.ape.springboot_01_marathon.controller;

import com.ape.springboot_01_marathon.common.Result;
import com.ape.springboot_01_marathon.pojo.vo.EventStatisticsVO;
import com.ape.springboot_01_marathon.pojo.vo.PublicEventVO;
import com.ape.springboot_01_marathon.service.MemberService;
import com.ape.springboot_01_marathon.service.StatisticsService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * C 端公开接口（免登录）。
 * <p>
 * <b>设计原则</b>：浏览不该设门槛。赛事列表、详情、公开榜单对游客开放，
 * 这是市面同类 App 的普遍做法 —— 强制登录才能看赛事会把流量卡死在第一步。
 * <p>
 * 已登录时（带上有效的 member Token）会额外返回「我是否已报名」这类
 * 与自己相关的信息，但<b>不登录也完全能看</b>。
 * <p>
 * 注意：本组接口只暴露公开字段，不返回任何选手的身份证/手机号。
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicController {

    private final MemberService memberService;
    private final StatisticsService statisticsService;

    /** 公开赛事列表 */
    @GetMapping("/events")
    public Result<List<PublicEventVO>> events(HttpServletRequest request) {
        Long runnerId = currentRunnerId(request);
        return Result.success(memberService.publicEvents(runnerId));
    }

    /** 公开赛事详情 */
    @GetMapping("/events/{id}")
    public Result<PublicEventVO> eventDetail(@PathVariable Long id, HttpServletRequest request) {
        Long runnerId = currentRunnerId(request);
        return Result.success(memberService.publicEventDetail(id, runnerId));
    }

    /**
     * 公开榜单。
     *
     * @param group total 总排名 / gender 男女分组 / age 年龄分组
     */
    @GetMapping("/events/{id}/rankings")
    public Result<Map<String, Object>> rankings(@PathVariable Long id,
                                                @RequestParam(defaultValue = "total") String group) {
        return Result.success(memberService.publicRankings(id, group));
    }

    /** 赛事公开统计：报名人数、男女比、完赛率 */
    @GetMapping("/events/{id}/stats")
    public Result<EventStatisticsVO> stats(@PathVariable Long id) {
        return Result.success(statisticsService.eventOverview(id));
    }

    /**
     * 尽力解析当前登录的选手 id。
     * <p>
     * 这里<b>不拦截</b>：Token 有就带上身份信息，没有就当游客。
     * 公开接口的 token 无效时也静默降级为游客，不返回 401。
     */
    private Long currentRunnerId(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (token == null || token.isBlank()) {
            token = request.getParameter("token");
        }
        if (token == null || token.isBlank()) {
            return null;
        }
        // Token 无效返回 null（游客），不会抛异常
        return memberService.resolveRunnerId(token);
    }
}

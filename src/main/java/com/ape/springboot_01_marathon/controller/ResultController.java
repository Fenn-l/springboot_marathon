package com.ape.springboot_01_marathon.controller;

import com.ape.springboot_01_marathon.common.PageResult;
import com.ape.springboot_01_marathon.common.Result;
import com.ape.springboot_01_marathon.pojo.ResultRecord;
import com.ape.springboot_01_marathon.pojo.dto.RegistrationQuery;
import com.ape.springboot_01_marathon.pojo.vo.RankVO;
import com.ape.springboot_01_marathon.pojo.vo.ResultVO;
import com.ape.springboot_01_marathon.service.ResultService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/result")
@RequiredArgsConstructor
public class ResultController {

    private final ResultService resultService;

    /** 录入成绩 */
    @PostMapping("/input")
    public Result<Map<String, Object>> input(@RequestBody ResultRecord record) {
        Long id = resultService.input(record);
        return Result.success(Map.of("id", id));
    }

    /** 修改成绩 */
    @PutMapping("/update")
    public Result<Void> update(@RequestBody ResultRecord record) {
        return resultService.update(record) ? Result.success() : Result.error("更新失败");
    }

    /** 赛事总排名 */
    @GetMapping("/eventRanking/{eventId}")
    public Result<PageResult<RankVO>> eventRanking(@PathVariable Long eventId,
                                                   @RequestParam(defaultValue = "1") Integer pageNum,
                                                   @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(resultService.eventRanking(eventId, pageNum, pageSize));
    }

    /** 男女分组排名 */
    @GetMapping("/genderRanking/{eventId}")
    public Result<Map<String, List<RankVO>>> genderRanking(@PathVariable Long eventId) {
        return Result.success(resultService.genderRanking(eventId));
    }

    /** 年龄分组排名 */
    @GetMapping("/ageRanking/{eventId}")
    public Result<Map<String, List<RankVO>>> ageRanking(@PathVariable Long eventId) {
        return Result.success(resultService.ageRanking(eventId));
    }

    /** 分页查询成绩记录 */
    @GetMapping("/list")
    public Result<PageResult<ResultVO>> list(@ModelAttribute RegistrationQuery query) {
        return Result.success(resultService.page(query));
    }
}

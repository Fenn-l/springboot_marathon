package com.ape.springboot_01_marathon.controller;

import com.ape.springboot_01_marathon.common.PageResult;
import com.ape.springboot_01_marathon.common.Result;
import com.ape.springboot_01_marathon.pojo.Runner;
import com.ape.springboot_01_marathon.pojo.dto.RunnerQuery;
import com.ape.springboot_01_marathon.service.RunnerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/runner")
@RequiredArgsConstructor
public class RunnerController {

    private final RunnerService runnerService;

    /** 新增选手档案 */
    @PostMapping("/add")
    public Result<Map<String, Object>> add(@Valid @RequestBody Runner runner) {
        Long id = runnerService.add(runner);
        return Result.success(Map.of("id", id));
    }

    /** 修改选手档案 */
    @PutMapping("/update")
    public Result<Void> update(@RequestBody Runner runner) {
        return runnerService.update(runner) ? Result.success() : Result.error("更新失败");
    }

    /** 分页查询选手 */
    @GetMapping("/list")
    public Result<PageResult<Runner>> list(@ModelAttribute RunnerQuery query) {
        return Result.success(runnerService.page(query));
    }

    /** 选手详情 */
    @GetMapping("/detail/{id}")
    public Result<Runner> detail(@PathVariable Long id) {
        return Result.success(runnerService.detail(id));
    }

    /** 删除选手 */
    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        return runnerService.delete(id) ? Result.success() : Result.error("删除失败");
    }
}

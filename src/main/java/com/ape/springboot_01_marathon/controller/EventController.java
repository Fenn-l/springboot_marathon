package com.ape.springboot_01_marathon.controller;

import com.ape.springboot_01_marathon.common.PageResult;
import com.ape.springboot_01_marathon.common.Result;
import com.ape.springboot_01_marathon.pojo.MarathonEvent;
import com.ape.springboot_01_marathon.pojo.dto.EventQuery;
import com.ape.springboot_01_marathon.pojo.vo.EventVO;
import com.ape.springboot_01_marathon.service.MarathonEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/event")
@RequiredArgsConstructor
public class EventController {

    private final MarathonEventService eventService;

    /** 新增赛事 */
    @PostMapping("/add")
    public Result<Map<String, Object>> add(@RequestBody MarathonEvent event) {
        Long id = eventService.add(event);
        return Result.success(Map.of("id", id));
    }

    /** 修改赛事 */
    @PutMapping("/update")
    public Result<Void> update(@RequestBody MarathonEvent event) {
        return eventService.update(event) ? Result.success() : Result.error("更新失败");
    }

    /** 分页多条件查询 */
    @GetMapping("/list")
    public Result<PageResult<EventVO>> list(@ModelAttribute EventQuery query) {
        return Result.success(eventService.page(query));
    }

    /** 赛事详情 */
    @GetMapping("/detail/{id}")
    public Result<EventVO> detail(@PathVariable Long id) {
        return Result.success(eventService.detail(id));
    }

    /** 删除赛事 */
    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        return eventService.delete(id) ? Result.success() : Result.error("删除失败");
    }
}

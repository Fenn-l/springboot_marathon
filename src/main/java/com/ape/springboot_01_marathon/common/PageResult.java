package com.ape.springboot_01_marathon.common;

import lombok.Data;

import java.util.List;

@Data
public class PageResult<T> {

    private Long total;
    private Integer pages;
    private Integer pageNum;
    private Integer pageSize;
    private List<T> list;

    public static <T> PageResult<T> of(List<T> list, Long total, Integer pageNum, Integer pageSize) {
        PageResult<T> p = new PageResult<>();
        p.setList(list);
        p.setTotal(total);
        p.setPageNum(pageNum);
        p.setPageSize(pageSize);
        // 向上取整算总页数，total 为 0 时 pages 给 0
        p.setPages(pageSize == null || pageSize == 0
                ? 0
                : (int) ((total + pageSize - 1) / pageSize));
        return p;
    }

    /** 空结果，查询无数据时避免前端拿到 null */
    public static <T> PageResult<T> empty(Integer pageNum, Integer pageSize) {
        return of(List.of(), 0L, pageNum, pageSize);
    }
}

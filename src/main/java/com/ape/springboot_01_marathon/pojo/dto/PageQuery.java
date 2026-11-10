package com.ape.springboot_01_marathon.pojo.dto;

import lombok.Data;

/**
 * 分页查询基类。
 *
 * <p>offset 必须在 Java 侧算好再传给 XML，因为 MyBatis 的 #{} 是 JDBC 预编译占位符，
 * 不支持写 OGNL 算术表达式。写成 #{(pageNum - 1) * pageSize} 时，
 * MyBatis 会把括号原样塞进 SQL，MySQL 直接报语法错误：
 * "You have an error in your SQL syntax ... near '(pageNum - 1) * pageSize'"。
 *
 * <p>正确姿势：Service 调 normalize() 算好 offset，XML 里写 LIMIT #{pageSize} OFFSET #{offset}。
 */
@Data
public class PageQuery {

    private Integer pageNum = 1;

    private Integer pageSize = 10;

    /** 偏移量，由 normalize() 根据 pageNum / pageSize 算出，XML 里直接用这个字段 */
    private Integer offset = 0;

    /**
     * 分页参数兜底 + 计算 offset。
     * 每个 Service 的 page() 方法开头都要调一次。
     */
    public void normalize() {
        if (pageNum == null || pageNum < 1) {
            pageNum = 1;
        }
        if (pageSize == null || pageSize < 1) {
            pageSize = 10;
        }
        if (pageSize > 100) {
            pageSize = 100;
        }
        offset = (pageNum - 1) * pageSize;
    }
}

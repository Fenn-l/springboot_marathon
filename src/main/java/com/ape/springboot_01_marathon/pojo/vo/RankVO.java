package com.ape.springboot_01_marathon.pojo.vo;

import lombok.Data;

@Data
public class RankVO {

    /** 名次 */
    private Integer rank;

    private String name;

    private String bibNumber;

    /** 性别 1男 2女，总排名返回，分组排名可不返回 */
    private Integer gender;

    /** 年龄，仅年龄分组返回 */
    private Integer age;

    /** 净成绩（秒） */
    private Integer netTime;

    /** 格式化后的净成绩 03:29:10 */
    private String netTimeStr;
}


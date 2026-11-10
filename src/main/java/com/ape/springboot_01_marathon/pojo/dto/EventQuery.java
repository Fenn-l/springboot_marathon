package com.ape.springboot_01_marathon.pojo.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
public class EventQuery extends PageQuery {

    /** 赛事名称模糊 */
    private String eventName;

    private Integer eventType;

    private Integer status;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    /** 清掉字符串前后空格，LIKE 才有意义 */
    public void trim() {
        if (eventName != null) {
            eventName = eventName.trim();
        }
    }
}

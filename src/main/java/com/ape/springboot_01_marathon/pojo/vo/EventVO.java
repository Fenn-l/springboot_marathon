package com.ape.springboot_01_marathon.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class EventVO {

    private Long id;
    private String eventName;
    private Integer eventType;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate eventDate;

    private String startLocation;
    private BigDecimal distanceKm;
    private Integer quota;
    private Integer registeredCount;

    /** 剩余名额 = quota - registeredCount，SQL 里算或 Service 里设 */
    private Integer remainQuota;

    private BigDecimal entryFee;
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}


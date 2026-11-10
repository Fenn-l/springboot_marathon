package com.ape.springboot_01_marathon.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class MarathonEvent {
    private Long id;

    /** 赛事类型：1全程马拉松 2半程马拉松 3迷你跑 */
    private Integer eventType;

    private String eventName;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate eventDate;

    private String startLocation;

    /** 距离（公里），DECIMAL(5,1) */
    private BigDecimal distanceKm;

    /** 总名额 */
    private Integer quota;

    /** 已报名人数 */
    private Integer registeredCount;

    private BigDecimal entryFee;

    /** 状态：0报名中 1报名截止 2赛事已结束 */
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}

package com.ape.springboot_01_marathon.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * C 端赛事卡片 / 详情。
 * <p>
 * 与管理端的 {@link EventVO} 相比，<b>只暴露公开信息</b>：
 * 不含内部字段、不含已报名者的敏感信息。
 */
@Data
public class PublicEventVO {

    private Long id;
    private String eventName;
    private Integer eventType;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate eventDate;

    private String startLocation;
    private BigDecimal distanceKm;
    private Integer quota;
    private Integer registeredCount;
    private Integer remainQuota;
    private BigDecimal entryFee;
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 距比赛还有多少天（负数表示已过赛），C 端做倒计时用 */
    private Integer daysLeft;

    /** 当前登录选手是否已报名该赛事（未登录为 null） */
    private Boolean registered;

    /** 我在这场的报名记录 id（未登录或未报名为 null） */
    private Long myRegistrationId;
}

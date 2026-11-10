package com.ape.springboot_01_marathon.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * C 端「我的报名」列表项。
 * <p>
 * 只含该选手自己的报名记录，身份证/手机号等敏感信息不出现在此。
 */
@Data
public class MyRegistrationVO {

    private Long id;
    private Long eventId;
    private String eventName;
    private Integer eventType;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private java.time.LocalDate eventDate;

    private String startLocation;
    private java.math.BigDecimal distanceKm;
    private java.math.BigDecimal entryFee;

    /** 号码布，未分配为 null */
    private String bibNumber;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime registerTime;

    /** 0未支付 1已支付 */
    private Integer payStatus;

    /** 1已报名 2已取消 3退赛 */
    private Integer status;

    /** 赛事状态：0报名中 1报名截止 2已结束 */
    private Integer eventStatus;

    /** 这条报名记录的 id，查证书时要用 */
    private Long myRegistrationId;

    /** 是否已检录 */
    private Boolean checkedIn;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime checkinTime;

    /** 我的成绩（未出成绩为 null） */
    private MyResultVO result;
}

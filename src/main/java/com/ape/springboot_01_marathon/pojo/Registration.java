package com.ape.springboot_01_marathon.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Registration {
    private Long id;

    private Long eventId;

    private Long runnerId;

    private String bibNumber;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime registerTime;

    /** 0未支付 1已支付 */
    private Integer payStatus;

    /** 1已报名 2已取消 3退赛 */
    private Integer status;

    /** 检录核销时间，未检录为 null（B 端现场工作台用） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime checkinTime;
}

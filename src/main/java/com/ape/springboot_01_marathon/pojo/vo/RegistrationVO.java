package com.ape.springboot_01_marathon.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class RegistrationVO {

    private Long id;
    private Long eventId;
    private Long runnerId;
    private String bibNumber;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime registerTime;

    private Integer payStatus;
    private Integer status;

    /** 关联选手简要信息 */
    private SimpleRunner runner;

    /** 关联赛事简要信息 */
    private SimpleEvent event;

    @Data
    public static class SimpleRunner {
        private Long id;
        private String name;
        private Integer gender;
        private String phone;
    }

    @Data
    public static class SimpleEvent {
        private Long id;
        private String eventName;

        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate eventDate;
    }
}


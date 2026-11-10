package com.ape.springboot_01_marathon.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ResultVO {

    private Long id;
    private Long registrationId;
    private Integer chipTime;
    private Integer netTime;
    private Integer finishStatus;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime finishTime;

    private String runnerName;
    private String bibNumber;

    /** 所属赛事 id，前端据此判断该赛事当前状态能不能改成绩 */
    private Long eventId;

    /** 所属赛事的当前状态：0报名中 1报名截止 2比赛中 3已结束 */
    private Integer eventStatus;
}

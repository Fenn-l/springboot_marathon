package com.ape.springboot_01_marathon.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ResultRecord {
    private Long id;

    private Long registrationId;

    /** 枪声成绩（秒） */
    private Integer chipTime;

    /** 净成绩（秒） */
    private Integer netTime;

    /** 1完赛 2未完赛 3退赛 */
    private Integer finishStatus;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime finishTime;
}

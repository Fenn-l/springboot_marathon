package com.ape.springboot_01_marathon.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * C 端「我的成绩」列表项。
 */
@Data
public class MyResultVO {

    /** 成绩记录 id */
    private Long id;

    /** 报名记录 id，用于查证书 */
    private Long registrationId;

    private Long eventId;
    private String eventName;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private java.time.LocalDate eventDate;

    private String bibNumber;

    /** 枪声成绩（秒） */
    private Integer chipTime;

    /** 净成绩（秒） */
    private Integer netTime;

    /** 净成绩格式化，如 03:29:10 */
    private String netTimeStr;

    /** 总名次（该赛事完赛选手中的名次） */
    private Integer rank;

    /** 1完赛 2未完赛 3退赛 */
    private Integer finishStatus;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime finishTime;

    /** 选手姓名（B 端证书名单用，C 端自己的成绩里也带上方便展示） */
    private String runnerName;

    /** 证书签发时间（B 端用），未签发为 null */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime certIssued;
}

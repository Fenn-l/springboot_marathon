package com.ape.springboot_01_marathon.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LoginVO {

    /** 后续请求带在请求头 Authorization: Bearer {token} */
    private String token;

    private Long adminId;

    private String username;

    private String adminName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime loginTime;
}
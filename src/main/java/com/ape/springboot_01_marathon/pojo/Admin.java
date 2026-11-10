package com.ape.springboot_01_marathon.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Admin {
    private Long id;

    private String username;

    /** JSON 序列化时忽略，接口绝不能把密码吐出去 */
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String password;

    private String adminName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}

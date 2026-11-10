package com.ape.springboot_01_marathon.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * C 端选手登录入参。
 */
@Data
public class MemberLoginDTO {

    @NotBlank(message = "手机号不能为空")
    private String phone;

    @NotBlank(message = "密码不能为空")
    private String password;
}

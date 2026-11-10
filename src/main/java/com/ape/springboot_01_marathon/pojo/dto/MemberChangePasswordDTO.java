package com.ape.springboot_01_marathon.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * C 端选手修改密码入参。
 */
@Data
public class MemberChangePasswordDTO {

    @NotBlank(message = "原密码不能为空")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 30, message = "新密码长度需在 6~30 位之间")
    private String newPassword;
}

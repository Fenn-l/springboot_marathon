package com.ape.springboot_01_marathon.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * B 端：管理员为选手开通账号 / 重置密码的入参。
 */
@Data
public class ResetPasswordDTO {

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 30, message = "密码长度需在 6~30 位之间")
    private String password;
}

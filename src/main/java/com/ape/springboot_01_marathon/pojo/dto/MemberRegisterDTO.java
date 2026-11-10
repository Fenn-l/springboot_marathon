package com.ape.springboot_01_marathon.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * C 端选手注册入参。
 * <p>
 * 注册 = 建一份选手档案 + 建一个登录账号（手机号）。
 * 档案字段的合法性复用 B 端 {@link com.ape.springboot_01_marathon.pojo.Runner}
 * 上的校验注解与 Service 校验逻辑。
 */
@Data
public class MemberRegisterDTO {

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "密码不能为空")
    private String password;

    @NotBlank(message = "姓名不能为空")
    private String name;

    /** 1男 2女 */
    private Integer gender;

    @NotBlank(message = "身份证号不能为空")
    @Pattern(regexp = "^\\d{17}[\\dXx]$", message = "身份证号格式不正确")
    private String idCard;

    @NotBlank(message = "出生日期不能为空")
    private String birthDate;

    @NotBlank(message = "紧急联系人不能为空")
    private String emergencyContact;
}

package com.ape.springboot_01_marathon.pojo.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class RunnerQuery extends PageQuery {

    /** 姓名模糊 */
    private String name;

    /** 手机号模糊 */
    private String phone;

    /** 1男 2女 */
    private Integer gender;

    public void trim() {
        if (name != null) {
            name = name.trim();
        }
        if (phone != null) {
            phone = phone.trim();
        }
    }
}

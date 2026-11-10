package com.ape.springboot_01_marathon.pojo.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class RegistrationQuery extends PageQuery {

    private Long eventId;

    /** 选手姓名模糊 */
    private String name;

    /** 0未支付 1已支付 */
    private Integer payStatus;

    /** 1已报名 2已取消 3退赛 */
    private Integer status;

    public void trim() {
        if (name != null) {
            name = name.trim();
        }
    }
}

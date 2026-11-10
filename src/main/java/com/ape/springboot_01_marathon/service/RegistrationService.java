package com.ape.springboot_01_marathon.service;

import com.ape.springboot_01_marathon.common.PageResult;
import com.ape.springboot_01_marathon.pojo.Registration;
import com.ape.springboot_01_marathon.pojo.dto.RegistrationQuery;
import com.ape.springboot_01_marathon.pojo.vo.RegistrationVO;

public interface RegistrationService {

    Long submit(Registration registration);

    boolean cancel(Long id);

    String assignBibNumber(Long id, String bibNumber);

    /** 按赛事类型规则建议下一枚号码布 */
    String suggestBib(Long id);

    /** 标记报名已支付 */
    boolean markPaid(Long id);

    /** 清空号码布，把 bib_number 置回 NULL */
    boolean clearBibNumber(Long id);

    PageResult<RegistrationVO> page(RegistrationQuery query);

    RegistrationVO detail(Long id);
}


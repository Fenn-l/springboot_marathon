package com.ape.springboot_01_marathon.common;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final Integer code;

    public BusinessException(String msg) {
        this(500, msg);
    }

    public BusinessException(Integer code, String msg) {
        super(msg);
        this.code = code;
    }
}


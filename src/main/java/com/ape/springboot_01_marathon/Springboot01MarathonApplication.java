package com.ape.springboot_01_marathon;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.ape.springboot_01_marathon.dao")
public class Springboot01MarathonApplication {

    public static void main(String[] args) {
        SpringApplication.run(Springboot01MarathonApplication.class, args);
    }
}

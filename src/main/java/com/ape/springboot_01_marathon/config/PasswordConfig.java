package com.ape.springboot_01_marathon.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 只引入 BCrypt 密码编码器，不引整套 Spring Security。
 * <p>
 * 为什么不直接明文存密码：
 * 1. 数据库一旦泄露，所有管理员密码直接暴露；
 * 2. BCrypt 自带随机盐，相同密码每次加密结果都不同，反彩虹表；
 * 3. 算法自带工作因子（默认 10 轮），可随硬件性能调高。
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
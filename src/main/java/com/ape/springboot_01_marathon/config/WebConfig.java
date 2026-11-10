package com.ape.springboot_01_marathon.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final MemberAuthInterceptor memberAuthInterceptor;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // B 端：管理员 Token，拦 /api/**
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/admin/login",   // 登录接口本身不需要 Token
                        "/api/public/**",     // C 端公开只读，游客可访问
                        "/api/member/**"      // C 端选手接口由下面的拦截器管
                );

        // C 端：选手 Token，只管 /api/member/**
        registry.addInterceptor(memberAuthInterceptor)
                .addPathPatterns("/api/member/**");
    }

    /**
     * C 端入口转发。
     * <p>
     * Spring Boot 的 welcome page 机制只对根路径 {@code /} 生效，
     * 访问 {@code /m/} 会直接 404（静态资源处理器不做目录 → index.html 的解析）。
     * 这里显式转发过去，让 {@code /m/} 和 {@code /m} 都能打开 C 端首页。
     * <p>
     * 用 forward 而不是重定向：URL 保持 {@code /m/}，页面里的绝对路径引用
     * （/m/style.css、/vendor/...）不受影响。
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/m").setViewName("forward:/m/index.html");
        registry.addViewController("/m/").setViewName("forward:/m/index.html");
    }
}
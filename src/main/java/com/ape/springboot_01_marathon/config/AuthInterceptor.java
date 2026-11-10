package com.ape.springboot_01_marathon.config;

import com.ape.springboot_01_marathon.common.ErrorCode;
import com.ape.springboot_01_marathon.common.TokenManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.HashMap;
import java.util.Map;

/**
 * 登录拦截器。
 * <p>
 * 拦下所有 /api/** 请求，要求带合法的 Token。放行登录接口和静态资源。
 * <p>
 * 注意：这里手动写 JSON 响应而不是抛异常，因为拦截器在进入 Controller 之前执行，
 * 抛出的异常走不到 {@code @RestControllerAdvice} 的异常处理。
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    /** 无需登录即可访问的路径 */
    private static final String[] WHITE_LIST = {
            "/api/admin/login",
            "/error"
    };

    private final TokenManager tokenManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        // OPTIONS 是浏览器跨域预检，直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String uri = request.getRequestURI();

        // 静态资源不拦
        if (!uri.startsWith("/api/")) {
            return true;
        }

        // 白名单放行
        for (String path : WHITE_LIST) {
            if (path.equals(uri)) {
                return true;
            }
        }

        // 取 Token：优先读请求头，退而求其次读 URL 参数（方便在地址栏直接调 GET 接口）
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (token == null || token.isBlank()) {
            token = request.getParameter("token");
        }

        Long adminId = tokenManager.verify(token);
        if (adminId == null) {
            writeJson(response, ErrorCode.UNAUTHORIZED, "未登录或登录已过期，请重新登录");
            return false;
        }

        // 把管理员 id 放进 request，Controller 里能用 request.getAttribute("adminId") 取
        request.setAttribute("adminId", adminId);
        return true;
    }

    /**
     * 手工写 JSON 响应。
     * 格式必须和后端统一返回值一致：{ code, msg, data }
     */
    private void writeJson(HttpServletResponse response, int code, String msg) throws Exception {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("msg", msg);
        body.put("data", null);

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
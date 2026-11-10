package com.ape.springboot_01_marathon.config;

import com.ape.springboot_01_marathon.common.ErrorCode;
import com.ape.springboot_01_marathon.common.TokenManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.HashMap;
import java.util.Map;

/**
 * 选手端（C 端）登录拦截器。
 * <p>
 * 拦下 {@code /api/member/**}，要求带 scope=member 的 Token。
 * 管理员 Token 拿这里会直接被拒 —— 两端凭据严格隔离。
 * <p>
 * 与 {@link AuthInterceptor} 的分工：
 * <ul>
 *   <li>AuthInterceptor 拦 {@code /api/**}，但排除 /api/public/** 与 /api/member/**</li>
 *   <li>本拦截器只管 /api/member/**</li>
 *   <li>/api/public/** 完全放行，游客可读</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class MemberAuthInterceptor implements HandlerInterceptor {

    /** 免登录的选手端路径 */
    private static final String[] WHITE_LIST = {
            "/api/member/register",
            "/api/member/login"
    };

    private final TokenManager tokenManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String uri = request.getRequestURI();
        for (String path : WHITE_LIST) {
            if (path.equals(uri)) {
                return true;
            }
        }

        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (token == null || token.isBlank()) {
            token = request.getParameter("token");
        }

        Long runnerId = tokenManager.verify(token, TokenManager.SCOPE_MEMBER);
        if (runnerId == null) {
            writeJson(response, ErrorCode.UNAUTHORIZED, "请先登录后再操作");
            return false;
        }

        // 选手 id 放进 request，Controller 里用 request.getAttribute("runnerId") 取
        request.setAttribute("runnerId", runnerId);
        return true;
    }

    /**
     * 手工写 JSON 响应，格式与后端统一返回值一致：{ code, msg, data }。
     */
    private void writeJson(HttpServletResponse response, int code, String msg) throws Exception {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> body = new HashMap<>(2);
        body.put("code", code);
        body.put("msg", msg);
        body.put("data", null);

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    /** 复用的 MediaType 常量 */
    private static final class MediaType {
        static final String APPLICATION_JSON_VALUE = "application/json;charset=UTF-8";
    }
}

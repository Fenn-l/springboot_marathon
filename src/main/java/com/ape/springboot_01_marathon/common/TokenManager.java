package com.ape.springboot_01_marathon.common;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token 管理器（内存实现）。
 * <p>
 * 登录成功后生成一个随机 Token 存进来，之后每次请求由拦截器取出并反查。
 * <p>
 * <b>局限</b>：Token 存在 JVM 内存里，<b>应用重启后全部失效</b>，用户需要重新登录。
 * 多实例部署时也无效（A 实例发的 Token 到 B 实例查不到）。
 * 生产环境应换成 Redis 或 JWT，这里为了不引额外依赖用内存版，够教学演示。
 *
 * <p>
 * <b>双端改造</b>：原来是「只认管理员」，现在泛化成「带 scope 的主体」，
 * 管理员（B 端）和选手（C 端）各签各的 Token，互不通用。
 * 原来 {@link #verify(String)} 返回管理员 id，现在多了
 * {@link #verifyToken(String)} 返回完整主体信息。
 */
@Slf4j
@Component
public class TokenManager {

    /** scope：管理员（B 端） */
    public static final String SCOPE_ADMIN = "admin";

    /** scope：选手（C 端） */
    public static final String SCOPE_MEMBER = "member";

    /**
     * Token -> TokenInfo。
     * <p>
     * 不能只存管理员 id：那样没法判断过期时间。
     * 早期版本这里存的是 {@code Map<String, Long>}，导致 {@link #EXPIRE_MILLIS}
     * 定义了却永远用不上，Token 实际上永不过期。现在把创建时间一起存下来。
     * <p>
     * 用 ConcurrentHashMap 避免并发写入问题。
     */
    private final Map<String, TokenInfo> tokenMap = new ConcurrentHashMap<>();

    /** Token 有效期：2 小时 */
    private static final long EXPIRE_MILLIS = 2 * 60 * 60 * 1000L;

    /**
     * Token 的附加信息。
     * <p>
     * 这里用静态内部类而不是 record —— Lombok 的 @Getter / @AllArgsConstructor
     * 不支持 record 类型。
     */
    @Getter
    @AllArgsConstructor
    static class TokenInfo {

        /** 这个 token 属于哪一端：{@link #SCOPE_ADMIN} 或 {@link #SCOPE_MEMBER} */
        private final String scope;

        /** 这个 token 属于哪个主体（admin.id 或 runner.id） */
        private final Long userId;

        /** 签发时刻（毫秒时间戳） */
        private final long createTime;

        /**
         * 是否已过期。
         */
        boolean expired(long now) {
            return now - createTime > EXPIRE_MILLIS;
        }
    }

    /**
     * 为管理员生成 Token（B 端）
     */
    public String create(Long adminId) {
        return create(SCOPE_ADMIN, adminId);
    }

    /**
     * 为指定主体生成 Token
     *
     * @param scope  {@link #SCOPE_ADMIN} 或 {@link #SCOPE_MEMBER}
     * @param userId 管理员 id 或选手 id
     */
    public String create(String scope, Long userId) {
        // UUID 随机性足够，且不依赖第三方库
        String token = UUID.randomUUID().toString().replace("-", "");
        tokenMap.put(token, new TokenInfo(scope, userId, System.currentTimeMillis()));
        log.info("{} {} 登录成功，Token 已生成，有效期 {} 小时",
                SCOPE_ADMIN.equals(scope) ? "管理员" : "选手", userId, EXPIRE_MILLIS / 3600 / 1000);
        return token;
    }

    /**
     * 校验 Token 并返回完整主体信息。
     *
     * @return 有效返回 TokenInfo，无效或过期返回 null
     */
    public TokenInfo verifyToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        TokenInfo info = tokenMap.get(token);
        if (info == null) {
            return null;
        }
        if (info.expired(System.currentTimeMillis())) {
            // 顺手清掉过期条目，避免 map 无限膨胀
            tokenMap.remove(token, info);
            log.info("Token 已过期，已自动清理");
            return null;
        }
        return info;
    }

    /**
     * 校验 Token 是否为指定 scope 的有效 Token。
     *
     * @return 有效返回该端的主体 id，无效/过期/scope 不符返回 null
     */
    public Long verify(String token, String scope) {
        TokenInfo info = verifyToken(token);
        if (info == null) {
            return null;
        }
        if (!info.getScope().equals(scope)) {
            // 端串了：B 端拿着 C 端的 Token 访问，反之亦然，一律拒绝
            return null;
        }
        return info.getUserId();
    }

    /**
     * 校验 Token 是否有效（存在且未过期，且是管理员 Token）。
     *
     * @return 有效返回管理员 id，无效或过期返回 null
     * @deprecated 管理员专用校验用 {@link #verify(String, String)} 更明确
     */
    public Long verify(String token) {
        return verify(token, SCOPE_ADMIN);
    }

    /**
     * 注销 Token（退出登录）
     */
    public void remove(String token) {
        if (token != null) {
            tokenMap.remove(token);
            log.info("Token 已注销");
        }
    }
}

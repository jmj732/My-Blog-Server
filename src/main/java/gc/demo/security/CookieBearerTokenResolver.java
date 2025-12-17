package gc.demo.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.stereotype.Component;

@Component
public class CookieBearerTokenResolver implements BearerTokenResolver {
    private static final String COOKIE_NAME = "JWT_TOKEN";

    @Override
    public String resolve(HttpServletRequest request) {
        // /api/v1/posts/sync 경로는 SyncTokenAuthenticationFilter에서 처리하므로 토큰 반환 안 함
        if (request.getRequestURI().equals("/api/v1/posts/sync")) {
            return null;
        }

        // 먼저 Authorization 헤더 확인
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        // 쿠키에서 JWT 토큰 찾기
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (COOKIE_NAME.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }

        return null;
    }
}

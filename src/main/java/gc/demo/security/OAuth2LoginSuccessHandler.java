package gc.demo.security;

import gc.demo.entity.User;
import gc.demo.repository.UserRepository;
import gc.demo.util.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    @Value("${jwt.cookie.max-age:86400}")
    private int cookieMaxAge;

    @Value("${jwt.cookie.domain:localhost}")
    private String cookieDomain;

    @Value("${jwt.cookie.secure:false}")
    private boolean cookieSecure;

    @Value("${jwt.cookie.same-site:none}")
    private String cookieSameSite;

    @Value("${frontend.callback-url:http://localhost:3000/api/auth/callback}")
    private String frontendCallbackUrl;

    public OAuth2LoginSuccessHandler(JwtTokenProvider jwtTokenProvider, UserRepository userRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        System.out.println("=== OAuth2LoginSuccessHandler - Authentication Success ===");

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");

        System.out.println("Email from OAuth2User: " + email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    System.err.println("ERROR: User not found in database for email: " + email);
                    return new RuntimeException("User not found");
                });

        System.out.println("User found: " + user.getEmail() + " (ID: " + user.getId() + ")");

        String token = jwtTokenProvider.generateToken(user);
        System.out.println("JWT token generated successfully");

        // Build cookie manually to control SameSite for cross-site scenarios
        boolean requireSecureForSameSiteNone = "none".equalsIgnoreCase(cookieSameSite);
        boolean secure = cookieSecure || requireSecureForSameSiteNone;
        String sameSiteAttr = normalizeSameSite(cookieSameSite);

        StringBuilder cookieValue = new StringBuilder();
        cookieValue.append("JWT_TOKEN=").append(token);
        cookieValue.append("; Path=/");
        cookieValue.append("; Max-Age=").append(cookieMaxAge);
        cookieValue.append("; HttpOnly");

        if (secure) {
            cookieValue.append("; Secure");
        }

        if (sameSiteAttr != null) {
            cookieValue.append("; SameSite=").append(sameSiteAttr);
        }

        if (cookieDomain != null && !cookieDomain.isEmpty()) {
            cookieValue.append("; Domain=").append(cookieDomain);
        }

        response.addHeader("Set-Cookie", cookieValue.toString());

        System.out.println("Cookie set with SameSite=None for cross-origin: " + cookieValue);
        String redirectUrl = UriComponentsBuilder.fromUriString(frontendCallbackUrl)
                .queryParam("token", token)
                .build()
                .encode()
                .toUriString();

        System.out.println("Redirecting to frontend with token: " + redirectUrl);
        response.sendRedirect(redirectUrl);
    }

    private String normalizeSameSite(String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.trim().toLowerCase();
        return switch (v) {
            case "none" -> "None";
            case "lax" -> "Lax";
            case "strict" -> "Strict";
            default -> null;
        };
    }
}

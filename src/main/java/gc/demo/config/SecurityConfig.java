package gc.demo.config;

import gc.demo.security.CookieBearerTokenResolver;
import gc.demo.security.OAuth2LoginSuccessHandler;
import gc.demo.security.SyncTokenAuthenticationFilter;
import gc.demo.service.CustomOAuth2UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Configuration
public class SecurityConfig {
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    private final CookieBearerTokenResolver cookieBearerTokenResolver;
    private final SyncTokenAuthenticationFilter syncTokenAuthenticationFilter;

    @Value("${frontend.url:http://localhost:3000}")
    private String frontendUrl;

    public SecurityConfig(CustomOAuth2UserService customOAuth2UserService,
                          OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler,
                          CookieBearerTokenResolver cookieBearerTokenResolver,
                          SyncTokenAuthenticationFilter syncTokenAuthenticationFilter) {
        this.customOAuth2UserService = customOAuth2UserService;
        this.oAuth2LoginSuccessHandler = oAuth2LoginSuccessHandler;
        this.cookieBearerTokenResolver = cookieBearerTokenResolver;
        this.syncTokenAuthenticationFilter = syncTokenAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(syncTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/oauth2/**", "/login/oauth2/**", "/oauth2/jwks").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/", "/health", "/api/v1/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/posts/**", "/api/v1/comments/**", "/api/v1/search/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").permitAll()
                        .requestMatchers("/api/v1/posts/sync").hasAuthority("SCOPE_sync")
                        .requestMatchers(HttpMethod.POST, "/api/v1/comments").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/comments/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/comments/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/posts").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/posts/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/posts/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/community/posts").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/community/posts/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/community/posts/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/me").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/search").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                        .successHandler(oAuth2LoginSuccessHandler)
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(cookieBearerTokenResolver)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                );
        return http.build();
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter roleConverter = new JwtGrantedAuthoritiesConverter();
        roleConverter.setAuthorityPrefix("ROLE_");
        roleConverter.setAuthoritiesClaimName("roles");

        JwtGrantedAuthoritiesConverter scopeConverter = new JwtGrantedAuthoritiesConverter();
        scopeConverter.setAuthorityPrefix("SCOPE_");
        scopeConverter.setAuthoritiesClaimName("scope");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>();
            Collection<GrantedAuthority> roleAuthorities = roleConverter.convert(jwt);
            Collection<GrantedAuthority> scopeAuthorities = scopeConverter.convert(jwt);
            if (roleAuthorities != null) {
                authorities.addAll(roleAuthorities);
            }
            if (scopeAuthorities != null) {
                authorities.addAll(scopeAuthorities);
            }
            return authorities;
        });
        return converter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(frontendUrl));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}

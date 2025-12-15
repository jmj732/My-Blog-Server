package gc.demo.service;

import gc.demo.domain.Role;
import gc.demo.entity.User;
import gc.demo.repository.UserRepository;
import gc.demo.util.Snowflake;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.util.*;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {
    private final UserRepository userRepository;
    private final Snowflake snowflake;

    public CustomOAuth2UserService(UserRepository userRepository, Snowflake snowflake) {
        this.userRepository = userRepository;
        this.snowflake = snowflake;
    }

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        Map<String, Object> attributes = new HashMap<>(oAuth2User.getAttributes());

        if ("github".equals(registrationId)) {
            String email = processGithubUser(userRequest, attributes);

            // Add email to attributes if it was fetched from /user/emails API
            if (email != null && !email.isEmpty()) {
                attributes.put("email", email);
                System.out.println("Added email to OAuth2User attributes: " + email);
            }
        }

        // Create new OAuth2User with updated attributes
        Set<GrantedAuthority> authorities = new HashSet<>(oAuth2User.getAuthorities());
        return new DefaultOAuth2User(authorities, attributes, "login");
    }

    private String processGithubUser(OAuth2UserRequest userRequest, Map<String, Object> attributes) {
        System.out.println("=== GitHub OAuth User Attributes ===");
        System.out.println("All attributes: " + attributes);

        String emailFromAttrs = (String) attributes.get("email");
        String name = (String) attributes.get("name");
        String avatarUrl = (String) attributes.get("avatar_url");

        System.out.println("Email from attributes: " + emailFromAttrs);

        // If email is null, fetch from GitHub /user/emails API
        String email = emailFromAttrs;
        if (email == null || email.isEmpty()) {
            System.out.println("Email is null, fetching from GitHub /user/emails API...");
            email = fetchPrimaryEmailFromGithub(userRequest);
            System.out.println("Fetched email: " + email);
        }

        System.out.println("Name: " + name);
        System.out.println("Avatar URL: " + avatarUrl);

        if (email == null || email.isEmpty()) {
            System.err.println("ERROR: Email is null or empty even after fetching from /user/emails!");
            throw new OAuth2AuthenticationException("Email not found from GitHub");
        }

        final String finalEmail = email;
        final String finalName = name;
        final String finalAvatarUrl = avatarUrl;

        userRepository.findByEmail(finalEmail)
                .ifPresentOrElse(
                        user -> {
                            user.setName(finalName != null ? finalName : user.getName());
                            user.setImage(finalAvatarUrl != null ? finalAvatarUrl : user.getImage());
                            userRepository.save(user);
                        },
                        () -> {
                            User newUser = new User();
                            newUser.setId(snowflake.nextId());
                            newUser.setEmail(finalEmail);
                            newUser.setName(finalName != null ? finalName : finalEmail);
                            newUser.setImage(finalAvatarUrl);
                            newUser.setRole(Role.USER);
                            newUser.setEmailVerified(OffsetDateTime.now());
                            userRepository.save(newUser);
                            System.out.println("Created new user: " + finalEmail);
                        }
                );

        return email;
    }

    private String fetchPrimaryEmailFromGithub(OAuth2UserRequest userRequest) {
        try {
            String accessToken = userRequest.getAccessToken().getTokenValue();

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            HttpEntity<?> entity = new HttpEntity<>(headers);

            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                "https://api.github.com/user/emails",
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );

            List<Map<String, Object>> emails = response.getBody();
            System.out.println("GitHub emails response: " + emails);

            if (emails != null && !emails.isEmpty()) {
                // Find primary email
                for (Map<String, Object> emailData : emails) {
                    Boolean isPrimary = (Boolean) emailData.get("primary");
                    Boolean isVerified = (Boolean) emailData.get("verified");
                    if (Boolean.TRUE.equals(isPrimary) && Boolean.TRUE.equals(isVerified)) {
                        String email = (String) emailData.get("email");
                        System.out.println("Found primary verified email: " + email);
                        return email;
                    }
                }

                // If no primary, use first verified email
                for (Map<String, Object> emailData : emails) {
                    Boolean isVerified = (Boolean) emailData.get("verified");
                    if (Boolean.TRUE.equals(isVerified)) {
                        String email = (String) emailData.get("email");
                        System.out.println("Found first verified email: " + email);
                        return email;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error fetching email from GitHub: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }
}

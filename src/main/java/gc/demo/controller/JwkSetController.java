package gc.demo.controller;

import com.nimbusds.jose.jwk.JWKSet;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "OAuth2", description = "OAuth2 인증 API")
@RestController
public class JwkSetController {
    private final JWKSet jwkSet;

    public JwkSetController(JWKSet jwkSet) {
        this.jwkSet = jwkSet;
    }

    @Operation(summary = "JWKS 공개키 조회", description = "OAuth2 JWT 검증을 위한 공개키 세트를 조회합니다")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping("/oauth2/jwks")
    public Map<String, Object> getJwkSet() {
        return jwkSet.toJSONObject();
    }
}

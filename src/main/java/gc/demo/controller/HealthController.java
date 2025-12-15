package gc.demo.controller;

import gc.demo.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Health", description = "헬스 체크 API")
@RestController
public class HealthController {

    @Operation(summary = "헬스 체크", description = "상태 200과 메시지를 반환합니다")
    @GetMapping({"/", "/health", "/api/v1/health"})
    public ApiResponse<String> health() {
        return ApiResponse.ok("ok");
    }
}

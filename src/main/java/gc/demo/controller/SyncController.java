package gc.demo.controller;

import gc.demo.common.ApiResponse;
import gc.demo.dto.request.SyncPostsRequest;
import gc.demo.dto.response.SyncResult;
import gc.demo.service.SyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;


@Tag(name = "Sync", description = "데이터 동기화 API")
@RestController
@RequestMapping("/api/v1/posts")
public class SyncController {
    private final SyncService syncService;

    public SyncController(SyncService syncService) {
        this.syncService = syncService;
    }

    @Operation(summary = "게시글 동기화", description = "외부 시스템과 게시글을 동기화합니다 (Authorization: Bearer 헤더 필요)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "동기화 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @PostMapping("/sync")
    public ApiResponse<SyncResult> sync(@Valid @RequestBody SyncPostsRequest request) {
        return ApiResponse.ok(syncService.sync(request));
    }
}

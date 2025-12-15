package gc.demo.controller;

import gc.demo.common.ApiResponse;
import gc.demo.dto.request.CommentCreateRequest;
import gc.demo.dto.response.CommentResponse;
import gc.demo.dto.request.CommentUpdateRequest;
import gc.demo.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Tag(name = "Comments", description = "댓글 관리 API")
@RestController
@RequestMapping("/api/v1/comments")
public class CommentController {
    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @Operation(summary = "댓글 목록 조회", description = "특정 게시글의 댓글 목록을 조회합니다")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping
    public ApiResponse<List<CommentResponse>> list(
            @Parameter(description = "게시글 ID", required = true) @RequestParam Long postId) {
        return ApiResponse.ok(commentService.list(postId));
    }

    @Operation(summary = "댓글 생성", description = "인증된 사용자가 댓글을 생성합니다",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "생성 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @PostMapping
    public ApiResponse<CommentResponse> create(
            @Valid @RequestBody CommentCreateRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        Long userId = extractUserId(jwt);
        return ApiResponse.ok(commentService.create(request, userId));
    }

    @Operation(summary = "댓글 수정", description = "인증된 사용자가 자신의 댓글을 수정합니다",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "댓글을 찾을 수 없음")
    })
    @PatchMapping("/{id}")
    public ApiResponse<CommentResponse> update(
            @Parameter(description = "댓글 ID", required = true) @PathVariable Long id,
            @Valid @RequestBody CommentUpdateRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        Long userId = extractUserId(jwt);
        boolean isAdmin = jwt.getClaimAsStringList("roles") != null && jwt.getClaimAsStringList("roles").contains("ADMIN");
        return ApiResponse.ok(commentService.update(id, request, userId, isAdmin));
    }

    @Operation(summary = "댓글 삭제", description = "인증된 사용자가 자신의 댓글을 삭제합니다",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "댓글을 찾을 수 없음")
    })
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @Parameter(description = "댓글 ID", required = true) @PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        Long userId = extractUserId(jwt);
        boolean isAdmin = jwt.getClaimAsStringList("roles") != null && jwt.getClaimAsStringList("roles").contains("ADMIN");
        commentService.softDelete(id, userId, isAdmin);
        return ApiResponse.ok(null);
    }

    private Long extractUserId(Jwt jwt) {
        Object claim = jwt.getClaim("userId");
        if (claim instanceof Number number) {
            return number.longValue();
        }
        String sub = jwt.getSubject();
        if (sub != null) {
            try {
                return Long.parseLong(sub);
            } catch (NumberFormatException ignored) {
            }
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "userId claim is missing or invalid");
    }
}

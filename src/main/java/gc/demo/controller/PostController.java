package gc.demo.controller;

import gc.demo.common.ApiResponse;
import gc.demo.dto.request.CommunityPostRequest;
import gc.demo.dto.request.PostCreateRequest;
import gc.demo.dto.request.PostUpdateRequest;
import gc.demo.dto.response.PostFeedResponse;
import gc.demo.dto.response.PostResponse;
import gc.demo.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;

@Tag(name = "Posts", description = "게시글 관리 API")
@RestController
@RequestMapping("/api/v1")
public class PostController {
    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @Operation(summary = "게시글 피드 조회", description = "커서 기반 페이지네이션으로 게시글 피드를 조회합니다")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping("/posts/feed")
    public ApiResponse<PostFeedResponse> feed(
            @Parameter(description = "조회할 게시글 수", example = "20") @RequestParam(defaultValue = "20") int limit,
            @Parameter(description = "커서 생성 시간") @RequestParam(required = false) String cursorCreatedAt,
            @Parameter(description = "커서 ID") @RequestParam(required = false) Long cursorId) {
        validateCursorParams(cursorCreatedAt, cursorId);
        OffsetDateTime cursor = cursorCreatedAt != null ? OffsetDateTime.parse(cursorCreatedAt) : null;
        return ApiResponse.ok(postService.getFeed(limit, cursor, cursorId));
    }

    @Operation(summary = "게시글 피드 조회 (타입 필터)", description = "커서 기반 페이지네이션으로 게시글 피드를 조회합니다 (admin/community 필터 지원)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청")
    })
    @GetMapping("/posts/cursor")
    public ApiResponse<PostFeedResponse> cursor(
            @Parameter(description = "조회할 게시글 수", example = "20") @RequestParam(defaultValue = "20") int limit,
            @Parameter(description = "커서 생성 시간") @RequestParam(required = false) String cursorCreatedAt,
            @Parameter(description = "커서 ID") @RequestParam(required = false) Long cursorId,
            @Parameter(description = "게시글 타입 (admin|community)", example = "community")
            @RequestParam(required = false) String type) {
        validateCursorParams(cursorCreatedAt, cursorId);
        validateFeedType(type);
        OffsetDateTime cursor = cursorCreatedAt != null ? OffsetDateTime.parse(cursorCreatedAt) : null;
        return ApiResponse.ok(postService.getFeed(limit, cursor, cursorId, type));
    }

    @Operation(summary = "게시글 목록 조회", description = "페이지 기반으로 게시글 목록을 조회합니다")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping("/posts")
    public ApiResponse<Page<PostResponse>> list(
            @Parameter(description = "페이지 번호", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기", example = "10") @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "게시글 타입") @RequestParam(required = false) String type) {
        return ApiResponse.ok(postService.list(type, page, pageSize));
    }

    @Operation(summary = "게시글 상세 조회", description = "slug를 통해 게시글 상세 정보를 조회합니다")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "게시글을 찾을 수 없음")
    })
    @GetMapping("/posts/{slug}")
    public ApiResponse<PostResponse> get(
            @Parameter(description = "게시글 slug", required = true) @PathVariable String slug) {
        String decoded = URLDecoder.decode(slug, StandardCharsets.UTF_8);
        return ApiResponse.ok(postService.getBySlug(decoded));
    }

    @Operation(summary = "관리자 게시글 생성", description = "관리자 권한으로 게시글을 생성합니다",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "생성 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음")
    })
    @PostMapping("/posts")
    public ApiResponse<SlugResponse> createAdmin(@Valid @RequestBody PostCreateRequest request) {
        String slug = postService.createAdminPost(request);
        return ApiResponse.ok(new SlugResponse(slug));
    }

    @Operation(summary = "관리자 게시글 수정", description = "관리자 권한으로 게시글을 수정합니다",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "게시글을 찾을 수 없음")
    })
    @PatchMapping("/posts/{slug}")
    public ApiResponse<Void> updateAdmin(
            @Parameter(description = "게시글 slug", required = true) @PathVariable String slug,
            @Valid @RequestBody PostUpdateRequest request) {
        String decoded = URLDecoder.decode(slug, StandardCharsets.UTF_8);
        postService.updateAdminPost(decoded, request);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "관리자 게시글 삭제", description = "관리자 권한으로 게시글을 삭제합니다",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "게시글을 찾을 수 없음")
    })
    @DeleteMapping("/posts/{slug}")
    public ApiResponse<Void> deleteAdmin(
            @Parameter(description = "게시글 slug", required = true) @PathVariable String slug) {
        String decoded = URLDecoder.decode(slug, StandardCharsets.UTF_8);
        postService.deleteAdminPost(decoded);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "커뮤니티 게시글 생성", description = "인증된 사용자가 커뮤니티 게시글을 생성합니다",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "생성 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @PostMapping("/community/posts")
    public ApiResponse<SlugResponse> createCommunity(
            @Valid @RequestBody CommunityPostRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        Long userId = extractUserId(jwt);
        String slug = postService.createCommunityPost(request, userId);
        return ApiResponse.ok(new SlugResponse(slug));
    }

    @Operation(summary = "커뮤니티 게시글 수정", description = "작성자(또는 ADMIN)가 커뮤니티 게시글을 수정합니다",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "게시글을 찾을 수 없음")
    })
    @PatchMapping("/community/posts/{slug}")
    public ApiResponse<Void> updateCommunity(
            @Parameter(description = "게시글 slug", required = true) @PathVariable String slug,
            @Valid @RequestBody PostUpdateRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        String decoded = URLDecoder.decode(slug, StandardCharsets.UTF_8);
        Long userId = extractUserId(jwt);
        boolean isAdmin = jwt.getClaimAsStringList("roles") != null && jwt.getClaimAsStringList("roles").contains("ADMIN");
        postService.updateCommunityPost(decoded, request, userId, isAdmin);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "커뮤니티 게시글 삭제", description = "작성자(또는 ADMIN)가 커뮤니티 게시글을 삭제합니다",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "게시글을 찾을 수 없음")
    })
    @DeleteMapping("/community/posts/{slug}")
    public ApiResponse<Void> deleteCommunity(
            @Parameter(description = "게시글 slug", required = true) @PathVariable String slug,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        String decoded = URLDecoder.decode(slug, StandardCharsets.UTF_8);
        Long userId = extractUserId(jwt);
        boolean isAdmin = jwt.getClaimAsStringList("roles") != null && jwt.getClaimAsStringList("roles").contains("ADMIN");
        postService.deleteCommunityPost(decoded, userId, isAdmin);
        return ApiResponse.ok(null);
    }

    public record SlugResponse(String slug) {}

    private void validateCursorParams(String cursorCreatedAt, Long cursorId) {
        boolean hasCreatedAt = cursorCreatedAt != null;
        boolean hasId = cursorId != null;
        if (hasCreatedAt != hasId) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cursorCreatedAt and cursorId must be provided together");
        }
    }

    private void validateFeedType(String type) {
        if (type == null) {
            return;
        }
        if (!type.equals("admin") && !type.equals("community")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "type must be either 'admin' or 'community'");
        }
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

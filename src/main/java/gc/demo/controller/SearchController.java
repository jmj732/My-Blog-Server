package gc.demo.controller;

import gc.demo.common.ApiResponse;
import gc.demo.dto.request.SearchRequest;
import gc.demo.dto.response.SearchResponse;
import gc.demo.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Search", description = "검색 API")
@RestController
@RequestMapping("/api/v1/search")
public class SearchController {
    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @Operation(summary = "게시글 검색 (GET)", description = "키워드로 게시글을 검색합니다")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "검색 성공")
    })
    @GetMapping
    public ApiResponse<SearchResponse> search(
            @Parameter(description = "검색 키워드", required = true, example = "spring") @RequestParam String q,
            @Parameter(description = "검색 결과 제한", example = "10") @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(searchService.search(q, limit));
    }

    @Operation(summary = "게시글 검색 (POST)", description = "검색 키워드(및 optional limit)를 POST 본문으로 받아 검색합니다")
    @PostMapping
    public ApiResponse<SearchResponse> search(@Valid @RequestBody SearchRequest request) {
        int limit = request.limit() == null ? 10 : request.limit();
        return ApiResponse.ok(searchService.search(request.q(), limit, request.embedding()));
    }
}

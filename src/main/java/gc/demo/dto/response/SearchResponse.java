package gc.demo.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

public record SearchResponse(List<Result> results, boolean fallback, String source) {
    public record Result(String slug, String title, String description, OffsetDateTime date, Double similarity) {}
}

package gc.demo.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

public record PostFeedResponse(List<Row> rows, NextCursor nextCursor) {
    public record Row(Long id, String slug, String title, Long authorId, OffsetDateTime createdAt) {}
    public record NextCursor(OffsetDateTime createdAt, Long id) {}
}

package gc.demo.dto.response;

import java.time.OffsetDateTime;

public record PostResponse(
        Long id,
        String slug,
        String title,
        String content,
        Long authorId,
        String authorName,
        String authorRole,
        OffsetDateTime createdAt,
        int version
) {
}

package gc.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.List;

public record SyncPostsRequest(@NotNull List<Item> posts) {
    public record Item(@NotNull Long id,
                       @NotBlank String slug,
                       @NotBlank String title,
                       @NotBlank String content,
                       OffsetDateTime createdAt,
                       Long authorId,
                       float[] embedding) {}
}

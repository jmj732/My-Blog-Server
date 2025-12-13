package gc.demo.dto.response;

import java.time.OffsetDateTime;

public record CommentResponse(Long id,
                              Long postId,
                              Long userId,
                              String userName,
                              boolean deleted,
                              String content,
                              OffsetDateTime createdAt,
                              OffsetDateTime updatedAt,
                              Long parentId) {
}

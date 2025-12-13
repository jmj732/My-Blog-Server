package gc.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CommentCreateRequest(
        @NotNull(message = "게시글 ID는 필수입니다")
        Long postId,

        Long parentId,

        @NotBlank(message = "댓글 내용은 필수입니다")
        @Size(min = 1, max = 1000, message = "댓글은 1자 이상 1000자 이하여야 합니다")
        String content
) {}

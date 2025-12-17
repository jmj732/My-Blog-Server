package gc.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PostUpdateRequest(
        @NotBlank(message = "제목은 필수입니다")
        @Size(min = 1, max = 255, message = "제목은 1자 이상 255자 이하여야 합니다")
        String title,

        @NotBlank(message = "내용은 필수입니다")
        @Size(min = 1, max = 50000, message = "내용은 1자 이상 50000자 이하여야 합니다")
        String content,

        float[] embedding
) {}

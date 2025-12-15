package gc.demo.dto.response;

public record AuthMeResponse(Long id,
                             String email,
                             String name,
                             String role,
                             String image) {
}

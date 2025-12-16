package gc.demo;

import gc.demo.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest
class PostRepositoryQueryTests {
    @Autowired
    private PostRepository postRepository;

    @Test
    void feedQuery_acceptsNullCursorOnFirstPage() {
        assertDoesNotThrow(() ->
                postRepository.findFeedFirst("admin", PageRequest.of(0, 1)));
    }
}

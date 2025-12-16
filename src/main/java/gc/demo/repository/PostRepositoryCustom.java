package gc.demo.repository;

import gc.demo.entity.Post;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface PostRepositoryCustom {
    List<Post> searchByEmbedding(float[] embedding, Pageable pageable);
}

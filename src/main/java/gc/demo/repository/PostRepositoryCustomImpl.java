package gc.demo.repository;

import gc.demo.converter.PgvectorStringFloatArrayConverter;
import gc.demo.entity.Post;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

@Repository
public class PostRepositoryCustomImpl implements PostRepositoryCustom {
    private static final int EMBEDDING_DIMENSION = 384;

    private final EntityManager entityManager;
    private final PgvectorStringFloatArrayConverter converter = new PgvectorStringFloatArrayConverter();

    public PostRepositoryCustomImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public List<Post> searchByEmbedding(float[] embedding, Pageable pageable) {
        if (embedding == null || embedding.length == 0 || pageable == null) {
            return Collections.emptyList();
        }

        String sql = """
                select *
                from post
                where embedding is not null
                order by embedding <=> CAST(:embedding AS vector(%d))
                limit :limit
                """.formatted(EMBEDDING_DIMENSION);

        Query query = entityManager.createNativeQuery(sql, Post.class);
        query.setParameter("embedding", converter.convertToDatabaseColumn(embedding));
        query.setParameter("limit", pageable.getPageSize());
        return query.getResultList();
    }
}

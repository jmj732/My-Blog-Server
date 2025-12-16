package gc.demo.repository;

import gc.demo.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long>, PostRepositoryCustom {
    Optional<Post> findBySlug(String slug);

    @Query("""
            select p from Post p
            where (:type is null)
               or (:type = 'admin' and p.author is null)
               or (:type = 'community' and p.author is not null)
            order by p.createdAt desc, p.id desc
            """)
    List<Post> findFeedFirst(@Param("type") String type, Pageable pageable);

    @Query("""
            select p from Post p
            where (p.createdAt < :cursorCreatedAt
               or (p.createdAt = :cursorCreatedAt and p.id < :cursorId))
            and ((:type is null)
               or (:type = 'admin' and p.author is null)
               or (:type = 'community' and p.author is not null))
            order by p.createdAt desc, p.id desc
            """)
    List<Post> findFeedAfter(@Param("type") String type,
                             @Param("cursorCreatedAt") OffsetDateTime cursorCreatedAt,
                             @Param("cursorId") Long cursorId,
                             Pageable pageable);

    @Query("""
            select p from Post p
            where (:type is null)
               or (:type = 'admin' and p.author is null)
               or (:type = 'community' and p.author is not null)
            """)
    Page<Post> findByType(@Param("type") String type, Pageable pageable);

    @Query("""
            select p from Post p
            where lower(p.title) like lower(concat('%', :q, '%'))
               or lower(p.content) like lower(concat('%', :q, '%'))
            order by p.createdAt desc
            """)
    List<Post> searchLexical(@Param("q") String q, Pageable pageable);
}

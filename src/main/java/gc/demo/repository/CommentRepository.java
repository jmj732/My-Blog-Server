package gc.demo.repository;

import gc.demo.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    @Query("""
            select c from Comment c
            join fetch c.user u
            where c.post.id = :postId
            order by c.createdAt asc
            """)
    List<Comment> findByPostIdWithUser(@Param("postId") Long postId);

    List<Comment> findByParentId(Long parentId);
}

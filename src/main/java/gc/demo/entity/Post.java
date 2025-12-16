package gc.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.ColumnTransformer;
import gc.demo.converter.PgvectorStringFloatArrayConverter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "post", indexes = {
        @Index(name = "idx_post_slug", columnList = "slug", unique = true),
        @Index(name = "idx_post_created_at", columnList = "created_at"),
        @Index(name = "idx_post_author_id", columnList = "author_id")
})
public class Post {
    @Id
    private Long id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String title;

    @Column(name = "content", columnDefinition = "text", nullable = false)
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private User author;

    @Convert(converter = PgvectorStringFloatArrayConverter.class)
    @ColumnTransformer(read = "embedding::text", write = "?::vector")
    @Column(name = "embedding", columnDefinition = "vector(384)")
    private float[] embedding;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Comment> comments = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Version
    @Column(name = "version")
    private int version;
}

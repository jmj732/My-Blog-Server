package gc.demo.service;

import gc.demo.dto.request.SyncPostsRequest;
import gc.demo.dto.response.SyncResult;
import gc.demo.entity.Post;
import gc.demo.entity.User;
import gc.demo.repository.PostRepository;
import gc.demo.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Set;

@Service
public class SyncService {
    private static final Logger log = LoggerFactory.getLogger(SyncService.class);
    private static final int EMBEDDING_DIMENSION = 384;

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final OpenAIEmbeddingClient embeddingClient;

    public SyncService(PostRepository postRepository,
                       UserRepository userRepository,
                       OpenAIEmbeddingClient embeddingClient) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.embeddingClient = embeddingClient;
    }

    @Transactional
    public SyncResult sync(SyncPostsRequest request) {
        int inserted = 0;
        int updated = 0;
        Set<String> incomingSlugs = new HashSet<>();

        for (var item : request.posts()) {
            incomingSlugs.add(item.slug());
            Post post = postRepository.findBySlug(item.slug()).orElse(null);
            User author = null;
            if (item.authorId() != null) {
                author = userRepository.findById(item.authorId())
                        .orElseThrow(() -> new NoSuchElementException("author not found: " + item.authorId()));
            }
            if (post == null) {
                post = new Post();
                post.setId(item.id());
                post.setSlug(item.slug());
                inserted++;
            } else {
                updated++;
            }
            post.setTitle(item.title());
            post.setContent(item.content());
            applyEmbedding(post, item.content(), item.embedding());
            post.setAuthor(author);
            postRepository.save(post);
        }

        // optional: delete posts not present; skipped to avoid destructive behavior
        return new SyncResult(request.posts().size(), inserted, updated, 0);
    }

    private void applyEmbedding(Post post, String content, float[] providedEmbedding) {
        if (providedEmbedding != null) {
            if (providedEmbedding.length == EMBEDDING_DIMENSION) {
                post.setEmbedding(providedEmbedding);
                return;
            }
            log.warn("Received embedding with length {} (expected {}); falling back to server-side generation",
                    providedEmbedding.length, EMBEDDING_DIMENSION);
        }
        embeddingClient.embed(content).ifPresent(post::setEmbedding);
    }
}

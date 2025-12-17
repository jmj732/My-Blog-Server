package gc.demo.service;

import gc.demo.dto.response.SearchResponse;
import gc.demo.entity.Post;
import gc.demo.repository.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class SearchService {
    private static final int MAX_LIMIT = 50;
    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private final PostRepository postRepository;
    private final OpenAIEmbeddingClient embeddingClient;

    public SearchService(PostRepository postRepository, OpenAIEmbeddingClient embeddingClient) {
        this.postRepository = postRepository;
        this.embeddingClient = embeddingClient;
    }

    public SearchResponse search(String q, int limit) {
        return search(q, limit, null);
    }

    public SearchResponse search(String q, int limit, List<Double> embeddingPayload) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
        Pageable pageable = PageRequest.of(0, safeLimit);
        float[] providedEmbedding = toFloatArray(embeddingPayload);
        Optional<float[]> queryEmbedding = Optional.ofNullable(providedEmbedding)
                .filter(vector -> vector.length > 0);

        if (queryEmbedding.isEmpty()) {
            queryEmbedding = embeddingClient.embed(q);
        }

        if (queryEmbedding.isPresent()) {
            float[] embeddingForSearch = queryEmbedding.get();
            List<Post> posts = postRepository.searchByEmbedding(embeddingForSearch, pageable);
            if (!posts.isEmpty()) {
                log.debug("query={} limit={} embedding=true results={} source=embeddings fallback=false",
                        q, safeLimit, posts.size());
                return new SearchResponse(convertToResults(posts, embeddingForSearch), false, "embeddings");
            }
            log.debug("query={} limit={} embedding=true but no hits -> falling back to lexical", q, safeLimit);
        }

        List<Post> posts = postRepository.searchLexical(q, pageable);
        log.debug("query={} limit={} embedding=false results={} source=lexical fallback=true", q, safeLimit, posts.size());
        return new SearchResponse(convertToResults(posts, null), true, "lexical");
    }

    private List<SearchResponse.Result> convertToResults(List<Post> posts, float[] queryEmbedding) {
        return posts.stream()
                .map(post -> toResult(post, queryEmbedding))
                .toList();
    }

    private SearchResponse.Result toResult(Post post, float[] queryEmbedding) {
        Double similarity = calculateSimilarity(queryEmbedding, post.getEmbedding());
        String description = snippet(post.getContent());
        return new SearchResponse.Result(
                post.getSlug(),
                post.getTitle(),
                description,
                post.getCreatedAt(),
                similarity
        );
    }

    private String snippet(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        return content.substring(0, Math.min(160, content.length()));
    }

    private Double calculateSimilarity(float[] queryEmbedding, float[] targetEmbedding) {
        if (queryEmbedding == null || targetEmbedding == null || queryEmbedding.length != targetEmbedding.length) {
            return null;
        }
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < queryEmbedding.length; i++) {
            dot += queryEmbedding[i] * targetEmbedding[i];
            normA += queryEmbedding[i] * queryEmbedding[i];
            normB += targetEmbedding[i] * targetEmbedding[i];
        }
        if (normA == 0 || normB == 0) {
            return null;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private float[] toFloatArray(List<Double> embeddingPayload) {
        if (embeddingPayload == null || embeddingPayload.isEmpty()) {
            return null;
        }
        float[] embedding = new float[embeddingPayload.size()];
        for (int i = 0; i < embeddingPayload.size(); i++) {
            Double value = embeddingPayload.get(i);
            embedding[i] = value == null ? 0 : value.floatValue();
        }
        return embedding;
    }
}

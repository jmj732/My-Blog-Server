package gc.demo.service;

import gc.demo.domain.Role;
import gc.demo.dto.request.CommunityPostRequest;
import gc.demo.dto.request.PostCreateRequest;
import gc.demo.dto.request.PostUpdateRequest;
import gc.demo.dto.response.PostFeedResponse;
import gc.demo.dto.response.PostResponse;
import gc.demo.entity.Post;
import gc.demo.entity.User;
import gc.demo.repository.PostRepository;
import gc.demo.repository.UserRepository;
import gc.demo.util.Snowflake;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class PostService {
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final Snowflake snowflake;
    private final OpenAIEmbeddingClient embeddingClient;

    public PostService(PostRepository postRepository,
                       UserRepository userRepository,
                       Snowflake snowflake,
                       OpenAIEmbeddingClient embeddingClient) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.snowflake = snowflake;
        this.embeddingClient = embeddingClient;
    }

    @Transactional(readOnly = true)
    public PostFeedResponse getFeed(int limit, OffsetDateTime cursorCreatedAt, Long cursorId) {
        return getFeed(limit, cursorCreatedAt, cursorId, null);
    }

    @Transactional(readOnly = true)
    public PostFeedResponse getFeed(int limit, OffsetDateTime cursorCreatedAt, Long cursorId, String type) {
        int safeLimit = Math.min(limit, 100);
        Pageable pageable = PageRequest.of(0, safeLimit);

        List<Post> posts = cursorCreatedAt == null
                ? postRepository.findFeedFirst(type, pageable)
                : postRepository.findFeedAfter(type, cursorCreatedAt, cursorId, pageable);

        List<PostFeedResponse.Row> rows = posts.stream()
                .map(p -> new PostFeedResponse.Row(
                        p.getId(),
                        p.getSlug(),
                        p.getTitle(),
                        p.getAuthor() != null ? p.getAuthor().getId() : null,
                        p.getCreatedAt()))
                .toList();

        PostFeedResponse.NextCursor nextCursor = posts.size() == safeLimit
                ? new PostFeedResponse.NextCursor(
                posts.get(posts.size() - 1).getCreatedAt(),
                posts.get(posts.size() - 1).getId())
                : null;
        return new PostFeedResponse(rows, nextCursor);
    }

    @Transactional(readOnly = true)
    public Page<PostResponse> list(String type, int page, int size) {
        PageRequest pageable = PageRequest.of(page, Math.min(size, 100));
        return postRepository.findByType(type, pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public PostResponse getBySlug(String slug) {
        return postRepository.findBySlug(slug)
                .map(this::toDto)
                .orElseThrow(() -> new NoSuchElementException("게시글을 찾을 수 없습니다"));
    }

    @Transactional
    public String createAdminPost(PostCreateRequest request) {
        Post post = new Post();
        post.setId(snowflake.nextId());
        post.setTitle(request.title());
        post.setContent(request.content());
        applyEmbedding(post, request.title(), request.content(), request.embedding());
        // Admin 게시글은 author를 설정하지 않음 (null로 유지)
        post.setSlug(generateUniqueSlug(request.title()));
        Post saved = postRepository.save(post);
        return saved.getSlug();
    }

    @Transactional
    public void updateAdminPost(String slug, PostUpdateRequest request) {
        Post post = postRepository.findBySlug(slug)
                .orElseThrow(() -> new NoSuchElementException("게시글을 찾을 수 없습니다"));
        post.setTitle(request.title());
        post.setContent(request.content());
        applyEmbedding(post, request.title(), request.content(), request.embedding());
    }

    @Transactional
    public void deleteAdminPost(String slug) {
        Post post = postRepository.findBySlug(slug)
                .orElseThrow(() -> new NoSuchElementException("게시글을 찾을 수 없습니다"));
        postRepository.delete(post);
    }

    @Transactional
    public void updateCommunityPost(String slug, PostUpdateRequest request, Long userId, boolean isAdmin) {
        Post post = postRepository.findBySlug(slug)
                .orElseThrow(() -> new NoSuchElementException("게시글을 찾을 수 없습니다"));
        if (!isAdmin) {
            if (post.getAuthor() == null || !Objects.equals(post.getAuthor().getId(), userId)) {
                throw new SecurityException("게시글 수정 권한이 없습니다");
            }
        }
        post.setTitle(request.title());
        post.setContent(request.content());
        applyEmbedding(post, request.title(), request.content(), request.embedding());
    }

    @Transactional
    public void deleteCommunityPost(String slug, Long userId, boolean isAdmin) {
        Post post = postRepository.findBySlug(slug)
                .orElseThrow(() -> new NoSuchElementException("게시글을 찾을 수 없습니다"));
        if (!isAdmin) {
            if (post.getAuthor() == null || !Objects.equals(post.getAuthor().getId(), userId)) {
                throw new SecurityException("게시글 삭제 권한이 없습니다");
            }
        }
        postRepository.delete(post);
    }

    @Transactional
    public String createCommunityPost(CommunityPostRequest request, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("사용자를 찾을 수 없습니다"));
        Post post = new Post();
        post.setId(snowflake.nextId());
        post.setTitle(request.title());
        post.setContent(request.content());
        applyEmbedding(post, request.title(), request.content(), null);
        post.setAuthor(user);
        post.setSlug(generateUniqueSlug(request.title()));
        Post saved = postRepository.save(post);
        return saved.getSlug();
    }

    private String generateUniqueSlug(String title) {
        String base = slugify(title);
        String candidate = base;
        int counter = 1;
        while (postRepository.findBySlug(candidate).isPresent()) {
            candidate = base + "-" + counter++;
        }
        return candidate;
    }

    private String slugify(String input) {
        String nowhitespace = input.trim().replaceAll("[\\s+]", "-");
        String normalized = Normalizer.normalize(nowhitespace, Normalizer.Form.NFD);
        String slug = normalized.replaceAll("[^\\w-]", "").toLowerCase(Locale.ROOT);
        return StringUtils.hasText(slug) ? slug : UUID.randomUUID().toString();
    }

    private void applyEmbedding(Post post, String title, String content, float[] providedEmbedding) {
        if (providedEmbedding != null) {
            if (providedEmbedding.length == 384) {
                post.setEmbedding(providedEmbedding);
                return;
            }
            // 잘못된 크기의 embedding은 무시하고 서버에서 생성
        }
        // title과 content를 결합하여 embedding 생성
        String textToEmbed = title + "\n" + content;
        embeddingClient.embed(textToEmbed).ifPresent(post::setEmbedding);
    }

    private PostResponse toDto(Post post) {
        Long authorId = post.getAuthor() != null ? post.getAuthor().getId() : null;
        String authorName = post.getAuthor() != null ? post.getAuthor().getName() : null;
        String authorRole = post.getAuthor() != null ? post.getAuthor().getRole().name() : Role.ADMIN.name();
        return new PostResponse(post.getId(), post.getSlug(), post.getTitle(), post.getContent(),
                authorId, authorName, authorRole, post.getCreatedAt(), post.getVersion());
    }
}

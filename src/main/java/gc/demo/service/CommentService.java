package gc.demo.service;

import gc.demo.dto.request.CommentCreateRequest;
import gc.demo.dto.response.CommentResponse;
import gc.demo.dto.request.CommentUpdateRequest;
import gc.demo.entity.Comment;
import gc.demo.entity.Post;
import gc.demo.entity.User;
import gc.demo.repository.CommentRepository;
import gc.demo.repository.PostRepository;
import gc.demo.repository.UserRepository;
import gc.demo.util.Snowflake;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
public class CommentService {
    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final Snowflake snowflake;

    public CommentService(CommentRepository commentRepository,
                          PostRepository postRepository,
                          UserRepository userRepository,
                          Snowflake snowflake) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.snowflake = snowflake;
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> list(Long postId) {
        return commentRepository.findByPostIdWithUser(postId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public CommentResponse create(CommentCreateRequest request, Long userId) {
        Post post = postRepository.findById(request.postId())
                .orElseThrow(() -> new NoSuchElementException("게시글을 찾을 수 없습니다"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("사용자를 찾을 수 없습니다"));

        Comment comment = new Comment();
        comment.setId(snowflake.nextId());
        comment.setPost(post);
        comment.setUser(user);
        comment.setContent(request.content());

        if (request.parentId() != null) {
            Comment parent = commentRepository.findById(request.parentId())
                    .orElseThrow(() -> new NoSuchElementException("부모 댓글을 찾을 수 없습니다"));
            comment.setParent(parent);
        }
        Comment saved = commentRepository.save(comment);
        return toDto(saved);
    }

    @Transactional
    public CommentResponse update(Long id, CommentUpdateRequest request, Long userId, boolean isAdmin) {
        Comment comment = commentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("댓글을 찾을 수 없습니다"));
        enforceOwner(comment, userId, isAdmin);
        comment.setContent(request.content());
        return toDto(comment);
    }

    @Transactional
    public void softDelete(Long id, Long userId, boolean isAdmin) {
        Comment comment = commentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("댓글을 찾을 수 없습니다"));
        enforceOwner(comment, userId, isAdmin);
        comment.setDeleted(true);
        removeIfNoVisibleDescendants(comment);
    }

    private void enforceOwner(Comment comment, Long userId, boolean isAdmin) {
        if (isAdmin) return;
        if (!comment.getUser().getId().equals(userId)) {
            throw new SecurityException("권한이 없습니다");
        }
    }

    private CommentResponse toDto(Comment c) {
        return new CommentResponse(
                c.getId(),
                c.getPost().getId(),
                c.getUser().getId(),
                c.getUser().getName(),
                c.isDeleted(),
                c.getContent(),
                c.getCreatedAt(),
                c.getUpdatedAt(),
                c.getParent() != null ? c.getParent().getId() : null
        );
    }

    private void removeIfNoVisibleDescendants(Comment comment) {
        if (!canDeletePermanently(comment)) {
            return;
        }
        Comment parent = comment.getParent();
        commentRepository.delete(comment);
        if (parent != null && parent.isDeleted()) {
            removeIfNoVisibleDescendants(parent);
        }
    }

    private boolean canDeletePermanently(Comment comment) {
        if (!comment.isDeleted()) {
            return false;
        }
        return commentRepository.findByParentId(comment.getId())
                .stream()
                .allMatch(this::isDeletedSubtree);
    }

    private boolean isDeletedSubtree(Comment comment) {
        if (!comment.isDeleted()) {
            return false;
        }
        return commentRepository.findByParentId(comment.getId())
                .stream()
                .allMatch(this::isDeletedSubtree);
    }
}

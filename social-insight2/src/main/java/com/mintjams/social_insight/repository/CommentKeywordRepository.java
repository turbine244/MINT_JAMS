package com.mintjams.social_insight.repository;

import com.mintjams.social_insight.entity.CommentKeyword;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommentKeywordRepository extends JpaRepository<CommentKeyword, Long> {
    Optional<CommentKeyword> findByCommentKeyword(String commentKeyword);
    List<CommentKeyword> findTop8ByOrderByScoreDesc();
}

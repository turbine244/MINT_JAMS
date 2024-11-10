package com.mintjams.social_insight.repository;

import com.mintjams.social_insight.entity.CommentKeyword;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CommentKeywordRepository extends JpaRepository<CommentKeywordRepository, Long> {
    Optional<CommentKeyword> findByCommentKey(String commentKeyword);


    // channel_id 기준으로 found 값이 높은 순서대로 commentKey와 found 조회
    @Query("SELECT ck.commentKey, ck.found FROM CommentKeyword ck WHERE ck.channel.id = :channelId ORDER BY ck.found DESC")
    List<Object[]> findCommentKeysAndFoundByChannelId(@Param("channelId") String channelId);


    // channel_id와 content_id 기준으로 found 값이 높은 순서대로 상위 8개의 commentKey와 found 조회
    @Query("SELECT ck.commentKey, ck.found FROM CommentKeyword ck " +
            "WHERE ck.channel.id = :channelId AND ck.content.id = :contentId " +
            "ORDER BY ck.found DESC")
    List<Object[]> findTop8CommentKeysAndFoundByChannelIdAndContentIdOrderByFoundDesc(
            @Param("channelId") String channelId,
            @Param("contentId") String contentId
    );

}

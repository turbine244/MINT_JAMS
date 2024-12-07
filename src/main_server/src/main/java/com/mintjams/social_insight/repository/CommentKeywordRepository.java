package com.mintjams.social_insight.repository;

import com.mintjams.social_insight.entity.Channel;
import com.mintjams.social_insight.entity.CommentKeyword;
import com.mintjams.social_insight.entity.Content;
import com.mintjams.social_insight.entity.ContentKeyword;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommentKeywordRepository extends JpaRepository<CommentKeyword, Long> {

    //누적
    Optional<CommentKeyword> findByCommentKeyAndChannelAndContent(String contentKey, Channel channel, Content content);

    //파이차트 (임시2)
    @Query(value = "SELECT c.content_id AS contentId, c.comment_key AS commentKey, c.found AS found " +
            "FROM comment_keyword c " +
            "WHERE c.channel_id = :channelId " +
            "ORDER BY c.found DESC " +
            "LIMIT 8", nativeQuery = true)
    List<Object[]> findTop8ByChannelId(@Param("channelId") String channelId);


    @Query(value = "SELECT ck.comment_Key, ck.found FROM comment_keyword ck" +
            " WHERE ck.content_id = :contentId ORDER BY ck.found " +
            "DESC LIMIT 8", nativeQuery = true)
    List<Object[]> findTopKeywordsByContentId(@Param("contentId") String contentId);

}

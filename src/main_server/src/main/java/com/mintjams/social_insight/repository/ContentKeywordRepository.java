package com.mintjams.social_insight.repository;

import com.mintjams.social_insight.entity.Channel;
import com.mintjams.social_insight.entity.ContentKeyword;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContentKeywordRepository extends JpaRepository<ContentKeyword, Long> {

    //누적
    Optional<ContentKeyword> findByContentKeyAndChannel(String contentKey, Channel channel);

    //10개만
    @Query(value = "SELECT * FROM content_keyword ck WHERE ck.channel_id = :channelId ORDER BY ck.found DESC LIMIT 10", nativeQuery = true)
    List<ContentKeyword> findTop10ByChannelIdOrderByFoundDesc(@Param("channelId") String channelId);


    @Query(
            value = "SELECT k.comment_key AS keyword, k.found FROM comment_keyword k WHERE k.channel_id = :channelId " +
                    "UNION ALL " +
                    "SELECT c.content_key AS keyword, c.found * 10 AS found FROM content_keyword c WHERE c.channel_id = :channelId " +
                    "ORDER BY found DESC LIMIT 50", nativeQuery = true
    )

    List<Object[]> findTop50ByChannelIdOrderByFoundDesc(@Param("channelId") String channelId);

}

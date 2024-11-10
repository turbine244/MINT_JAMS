package com.mintjams.social_insight.repository;

import com.mintjams.social_insight.entity.ContentKeyword;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ContentKeywordRepository extends JpaRepository<ContentKeyword, Long> {

    // channel_id 기준으로 found 값이 높은 순서대로 contentKey와 found 조회
    @Query("SELECT c.contentKey, c.found FROM Content c WHERE c.channel.id = :channelId ORDER BY c.found DESC")
    List<Object[]> findContentKeysAndFoundByChannelId(@Param("channelId") String channelId);


    // channel_id 기준으로 found 값이 높은 순서대로 상위 10개의 contentKey와 found 조회
    @Query("SELECT c.contentKey, c.found FROM Content c WHERE c.channel.id = :channelId ORDER BY c.found DESC")
    List<Object[]> findTop10ContentKeysAndFoundByChannelIdOrderByFoundDesc(
            @Param("channelId") String channelId,
            Pageable pageable
    );

}

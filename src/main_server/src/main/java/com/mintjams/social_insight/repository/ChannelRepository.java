package com.mintjams.social_insight.repository;

import com.mintjams.social_insight.entity.Channel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface ChannelRepository extends JpaRepository<Channel, String> {

    Optional<Channel> findByChannelId(String channelId);

    // sentiment 값만 업데이트
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE channel c SET c.sentiment = :sentiment WHERE c.channelId = :channelId")
    int updateSentimentByChannelId(@Param("channelId") String channelId, @Param("sentiment") Double sentiment);

}

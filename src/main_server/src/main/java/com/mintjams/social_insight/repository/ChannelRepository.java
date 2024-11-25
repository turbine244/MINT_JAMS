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

//    @Query(value = "SELECT c.sentiment FROM Channel c WHERE c.channel_Id = :channelId", nativeQuery = true)
//    Double findSentimentByChannelId(@Param("channelId") String channelId);
}

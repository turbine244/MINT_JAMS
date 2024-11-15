package com.mintjams.social_insight.repository;

import com.mintjams.social_insight.entity.Content;

import jakarta.persistence.Tuple;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ContentRepository extends JpaRepository<Content, String> {
    @Query("SELECT c.contentId FROM content c WHERE c.channel.channelId = :channelId")
    List<String> findContentIdsByChannelId(@Param("channelId") String channelId);

    // 특정 채널 아이디와 연관된 모든 Content의 commentNum을 가져오는 쿼리
    @Query("SELECT c.commentNum FROM content c WHERE c.channel.channelId = :channelId")
    List<Integer> findCommentNumsByChannelId(@Param("channelId") String channelId);


}
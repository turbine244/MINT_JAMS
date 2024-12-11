package com.mintjams.social_insight.repository;

import com.mintjams.social_insight.entity.Channel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChannelRepository extends JpaRepository<Channel, String> {
    Optional<Channel> findByChannelId(String channelId);

    // 최근 검색어: updatedAt 기준 상위 10개
    List<Channel> findByOrderByUpdatedAtDesc(Pageable pageable);

    // 인기 검색어: anchorNum 기준 상위 10개
    List<Channel> findByOrderByAnchorNumDesc(Pageable pageable);

    // anchorNum 기준으로 내림차순 정렬된 모든 데이터 가져오기
    List<Channel> findAllByOrderByAnchorNumDesc();

    // Id에 해당하는 updateAnchorNum 가져오기
    @Query("SELECT c.updateAnchorNum FROM channel c WHERE c.channelId = :channelId")
    Integer findUpdateAnchorNumByChannelId(@Param("channelId") String channelId);
}
package com.mintjams.social_insight.repository;

import com.mintjams.social_insight.entity.Channel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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

}

package com.mintjams.social_insight.repository;

import com.mintjams.social_insight.entity.Video;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VideoRepository extends JpaRepository<Video, Long> {
    Video findByVideoId(String videoId);
}

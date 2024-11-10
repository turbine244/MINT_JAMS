package com.mintjams.social_insight.repository;

import com.mintjams.social_insight.entity.ContentKeyword;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentRepository extends JpaRepository<ContentKeyword, Long> {
   // Optional<ContentKeyword> findByContentKeyword(String keyword);

}

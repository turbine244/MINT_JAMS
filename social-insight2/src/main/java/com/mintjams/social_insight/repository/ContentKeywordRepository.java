package com.mintjams.social_insight.repository;

import com.mintjams.social_insight.entity.ContentKeyword;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ContentKeywordRepository  extends JpaRepository<ContentKeyword, Long> {
   // Optional<ContentKeyword> findByContentKeyword(String keyword);

}

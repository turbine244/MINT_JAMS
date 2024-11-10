package com.mintjams.social_insight.repository;

import com.mintjams.social_insight.entity.Keyword;
import org.springframework.data.jpa.repository.JpaRepository;


//Keyword 엔티티와 관련된 데이터베이스 작업
public interface KeywordRepository extends JpaRepository<Keyword, Long> {
}

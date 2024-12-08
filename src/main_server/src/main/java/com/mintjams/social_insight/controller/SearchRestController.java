package com.mintjams.social_insight.controller;

import com.mintjams.social_insight.dto.*;
import com.mintjams.social_insight.service.ApiService;
import com.mintjams.social_insight.service.YouTubeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class SearchRestController {

    private final YouTubeService youTubeService;
    private final ApiService apiService;

    public SearchRestController(YouTubeService youTubeService, ApiService apiService) {
        this.youTubeService = youTubeService;
        this.apiService = apiService;
    }

    // 1. 채널 정보
    @GetMapping("/channel")
    public ChannelDTO getChannelData(@RequestParam("channelTitle") String channelTitle) {
        if (channelTitle == null || channelTitle.trim().isEmpty()) {
            throw new IllegalArgumentException("channelTitle is required");
        }

        String apiKey = apiService.callApi(channelTitle);
        ChannelDTO channelDTO = youTubeService.getChannelData(channelTitle, apiKey);

        if (channelDTO == null || channelDTO.getChannelId() == null) {
            throw new IllegalStateException("No channel data found for the given title");
        }

        String channelId = channelDTO.getChannelId();
        if (!youTubeService.isChannelIdExists(channelId)) {
            youTubeService.saveChannelData(channelDTO, apiKey);
        }
        youTubeService.checkUpdate(channelId, apiKey);

        return youTubeService.getChannelDBData(channelDTO);
    }

    // 2. 워드클라우드 데이터
    @GetMapping("/wordcloud")
    public WordCloudDTO getWordCloudData(@RequestParam("channelId") String channelId) {
        if (channelId == null || channelId.trim().isEmpty()) {
            throw new IllegalArgumentException("channelId is required");
        }

        WordCloudDTO wordCloudData = youTubeService.getWordCloudData(channelId);
        if (wordCloudData == null) {
            throw new IllegalStateException("No word cloud data found for the given channelId");
        }

        return wordCloudData;
    }

    // 3. 랭킹 차트 데이터
    @GetMapping("/ranking")
    public KeywordDTO getRankingData(@RequestParam("channelId") String channelId) {
        if (channelId == null || channelId.trim().isEmpty()) {
            throw new IllegalArgumentException("channelId is required");
        }

        KeywordDTO rankingData = youTubeService.getRankingData(channelId);
        if (rankingData == null) {
            throw new IllegalStateException("No ranking data found for the given channelId");
        }

        return rankingData;
    }

    // 4. 파이 차트 데이터
    @GetMapping("/piechart")
    public List<PieDTO> getPieData(@RequestParam("channelId") String channelId) {
        if (channelId == null || channelId.trim().isEmpty()) {
            throw new IllegalArgumentException("channelId is required");
        }

        List<PieDTO> pieData = youTubeService.getPieData(channelId);
        if (pieData == null || pieData.isEmpty()) {
            throw new IllegalStateException("No pie chart data found for the given channelId");
        }

        return pieData;
    }

    //index 최근 검색어
    @GetMapping("/recent")
    public ResponseEntity<List<SearchTrendsDTO>> getRecentKeywords() {
        List<SearchTrendsDTO> recentKeywords = youTubeService.getRecentKeywords();
        return ResponseEntity.ok(recentKeywords);
    }

    //index 인기 검색어
    @GetMapping("/popular")
    public ResponseEntity<List<SearchTrendsDTO>> getPopularKeywords() {
        List<SearchTrendsDTO> popularKeywords = youTubeService.getPopularKeywords();
        return ResponseEntity.ok(popularKeywords);
    }


}

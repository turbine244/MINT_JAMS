package com.mintjams.social_insight.controller;

import com.mintjams.social_insight.dto.ChannelDTO;
import com.mintjams.social_insight.dto.KeywordDTO;
import com.mintjams.social_insight.dto.PieDTO;
import com.mintjams.social_insight.dto.WordCloudDTO;
import com.mintjams.social_insight.service.YouTubeService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class SearchRestController {

    private final YouTubeService youTubeService;

    public SearchRestController(YouTubeService youTubeService) {
        this.youTubeService = youTubeService;
    }

    // 1. 채널 정보
    @GetMapping("/channel")
    public ChannelDTO getChannelData(@RequestParam("channelTitle") String channelTitle) {
        String apiKey = "AIzaSyAlR7JJ-b8yB_oaid6UVJZ_moFgRNW7bXQ";

        ChannelDTO channelDTO = youTubeService.getChannelData(channelTitle, apiKey);

        // 데이터가 없으면 저장하고 업데이트
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
        return youTubeService.getWordCloudData(channelId);
    }

    // 3. 랭킹 차트 데이터
    @GetMapping("/ranking")
    public KeywordDTO getRankingData(@RequestParam("channelId") String channelId) {
        return youTubeService.getRankingData(channelId);
    }

    // 4. 파이 차트 데이터
    @GetMapping("/piechart")
    public List<PieDTO> getPieData(@RequestParam("channelId") String channelId) {
        return youTubeService.getPieData(channelId);
    }
}

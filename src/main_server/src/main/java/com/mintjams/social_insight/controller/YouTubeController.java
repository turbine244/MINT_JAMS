package com.mintjams.social_insight.controller;

import com.mintjams.social_insight.dto.ChannelDTO;
import com.mintjams.social_insight.dto.KeywordDTO;
import com.mintjams.social_insight.service.FlaskQueueService;
import com.mintjams.social_insight.service.YouTubeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Controller
public class YouTubeController {

    private final YouTubeService youTubeService;
    private final FlaskQueueService taskQueueService;

    // Constructor injection for the service
    @Autowired
    public YouTubeController(YouTubeService youTubeService, FlaskQueueService taskQueueService) {
        this.youTubeService = youTubeService;
        this.taskQueueService = taskQueueService;
    }

    // 기본
    @GetMapping("/")
    public String getPage() {
        return "index"; // Return the index.html page
    }

    // 검색
    @PostMapping("/search")
    public String info(@RequestParam("channelTitle") String channelTitle, Model model) {

        String apiKey = "AIzaSyAlR7JJ-b8yB_oaid6UVJZ_moFgRNW7bXQ"; // API 키

        // 채널 정보 가져오기
        ChannelDTO channelDTO = youTubeService.getChannelData(channelTitle, apiKey);

        // 채널 ID 저장하기
        String channelId = channelDTO.getChannelId();

        // 채널 ID 조회
        if (!(youTubeService.isChannelIdExists(channelId))) {
            // 채널 ID가 존재하지 않을 경우 -> Channel과 Content DB에 새로운 정보를 저장
            youTubeService.saveChannelData(channelDTO, apiKey);
        }

        System.out.println("채널 업데이트 작업 시작: " + channelId);
        youTubeService.checkUpdate(channelId, apiKey); // DB 갱신 코드
        System.out.println("채널 업데이트 작업 완료: " + channelId);

        channelDTO = youTubeService.getChannelDBData(channelDTO);

        model.addAttribute("channel", channelDTO);

        // 워드클라우드 그래프 - 모든 키워드 상위 100개
        model.addAttribute("wordCloud", youTubeService.getWordCloudData(channelId));

        // 주제 키워드 랭킹 - 본문 키워드 상위 10개
        model.addAttribute("rankingChart", youTubeService.getRankingData(channelId));

        // 파이 차트 - 댓글 키워드 (동영상 당 8개) -> 얘는 나중에 content 아이디 추가해야됨
        model.addAttribute("pieChart", youTubeService.getPieData(channelId));

        // 월벌 채널 변화량
        // model.addAttribute("growthChart", youTubeService.getGrowthData(channelId));

        return "info";
    }

}
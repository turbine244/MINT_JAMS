package com.mintjams.social_insight.controller;

import com.mintjams.social_insight.dto.ChannelDTO;
import com.mintjams.social_insight.dto.KeywordDTO;
import com.mintjams.social_insight.service.YouTubeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@Controller
public class YouTubeController {

    private final YouTubeService youTubeService;

    // Constructor injection for the service
    @Autowired
    public YouTubeController(YouTubeService youTubeService) {

        this.youTubeService = youTubeService;
    }


    //기본
    @GetMapping("/")
    public String getPage() {
        return "index";  // Return the index.html page
    }



    //검색
    @PostMapping("/search")
    public String info(@RequestParam("channelTitle") String channelTitle, Model model ) {

        String apiKey = "AIzaSyBOnvoVM2O60KA30ReM-No_OzcvQjjk68w"; // API 키

        //채널 정보 가져오기
        ChannelDTO channelDTO = youTubeService.getChannelData(channelTitle, apiKey);
        model.addAttribute("channel", channelDTO);

        //채널 ID 저장하기
        String channelId = channelDTO.getChannelId();

        //채널 DTO 값이 DB정보와 차이가 '없을' 경우에는, DB정보만 끌고 오면 됨


        /*

        채널 DTO와 DB 비교 코드 구현 전

         */


        //채널 DTO 값이 DB정보와 차이가 '있을' 경우에 아래 코드가 실행

        //DB 갱신 코드
        youTubeService.setKeywordData(channelId, apiKey);

        //워드클라우드 그래프 - 모든 키워드 상위 100개
        model.addAttribute("wordCloud", youTubeService.getWordCloudData(channelId));

        //주제 키워드 랭킹 - 본문 키워드 상위 10개
        model.addAttribute("rankingChart", youTubeService.getRankingData(channelId));

        //파이 차트 - 댓글 키워드 (동영상 당 8개) -> 얘는 나중에 content 아이디 추가해야됨
        model.addAttribute("pieChart", youTubeService.getPieData(channelId));

        //월벌 채널 변화량
        model.addAttribute("growthChart", youTubeService.getGrowthData(channelId));

        return "info";
    }




}

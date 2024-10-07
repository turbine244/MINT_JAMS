package com.mintjams.social_insight.controller;

import com.mintjams.social_insight.dto.VideoDataDTO;
import com.mintjams.social_insight.service.YouTubeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class YouTubeController {

    private final YouTubeService youTubeService;

    // Constructor injection for the service
    @Autowired
    public YouTubeController(YouTubeService youTubeService) {
        this.youTubeService = youTubeService;
    }


    // Display form for videoId input
    @GetMapping("/youtube")
    public String getYouTubeInputPage() {
        return "youtube-input";  // Return the youtube-input.html page
    }

    @GetMapping("/youtube/info")
    public String getVideoInfo(@RequestParam("videoId") String videoId, Model model) {

        //String videoId = "qWbHSOplcvY"; // 동영상 ID를 입력하세요
        String apiKey = "AIzaSyBOnvoVM2O60KA30ReM-No_OzcvQjjk68w"; // API 키
        String flaskUrl = "http://localhost:5000/extract_keywords";  // Flask 서버 URL

        try {
            // Service를 사용하여 동영상 정보를 가져옴
            VideoDataDTO videoDataDTO = youTubeService.getVideoData(videoId, apiKey, flaskUrl);

            // 모델에 DTO를 추가하여 HTML에 전달
            model.addAttribute("video", videoDataDTO);
            // "video-info.html" 템플릿을 반환
            return "video-info";

        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("error", "Could not retrieve video information. Please try again.");
            return "youtube-input";
        }


    }
}

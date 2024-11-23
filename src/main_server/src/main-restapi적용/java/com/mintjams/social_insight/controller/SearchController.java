package com.mintjams.social_insight.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class SearchController {

    // /search 경로 처리
    @GetMapping("/search")
    public String searchPage(@RequestParam("channelTitle") String channelTitle) {
        // 받은 채널 제목을 사용해 API 호출하거나 데이터를 처리할 수 있습니다.
        System.out.println("Channel Title: " + channelTitle); // 디버깅용

        // 예시: model.addAttribute("channelTitle", channelTitle); -> 템플릿에서 사용 가능
        return "search"; // templates/search.html을 반환
    }
}

package com.mintjams.social_insight.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor

public class VideoDataDTO {
    private String videoId;
    private String title;
    private String description;
    private String publishedAt;
    private String viewCount;
    private String likeCount;


}

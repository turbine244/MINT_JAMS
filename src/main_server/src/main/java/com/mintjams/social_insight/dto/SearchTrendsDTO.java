package com.mintjams.social_insight.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SearchTrendsDTO {
    private String channelTitle;
    private Integer view;
    private LocalDateTime time;
}

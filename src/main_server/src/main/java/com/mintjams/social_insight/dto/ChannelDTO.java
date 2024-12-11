package com.mintjams.social_insight.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class ChannelDTO {
    private String channelId; //DB
    private String createdAt;
    private String channelTitle;
    private String channelUrl;
    private String subscriberCount;
    private String channelThumbnail;
    private int rank; //DB

    private Integer contentNum; //DB
    private LocalDateTime updatedAt; //DB
    private String createdAtDB;//DB
    private Integer anchorNum; //DB
    private Integer updateAnchorNum;
}

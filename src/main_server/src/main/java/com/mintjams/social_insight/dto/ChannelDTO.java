package com.mintjams.social_insight.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ChannelDTO {
    private String channelId; //DB
    private String channelTitle;
    private String channelUrl;
    private String createdAt;
    private String subscriberCount;
    private Integer contentNum; //DB
    private String channelThumbnail;


}

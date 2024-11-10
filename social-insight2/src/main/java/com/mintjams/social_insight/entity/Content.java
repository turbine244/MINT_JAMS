package com.mintjams.social_insight.entity;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity(name = "content_keyword")
@Getter
@Setter
@NoArgsConstructor
public class Content {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) //auto_increment
    private Long id;

    //채널id(외래키)
    @ManyToOne
    @JoinColumn(name = "channel_id", referencedColumnName = "channelId", nullable = false)
    private Channel channel;

    //컨텐트id
    @Column(unique = true ,nullable = false)
    private String contentId;


}



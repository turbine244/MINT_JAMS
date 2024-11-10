package com.mintjams.social_insight.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity(name = "content")
@Getter
@Setter
@NoArgsConstructor
public class ContentKeyword {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //채널id(외래키)
    @ManyToOne
    @JoinColumn(name = "channel_id", referencedColumnName = "channelId", nullable = false)
    private Channel channel;

    @Column(unique = true)
    private String contentKey;

    @Column
    private int found;


}

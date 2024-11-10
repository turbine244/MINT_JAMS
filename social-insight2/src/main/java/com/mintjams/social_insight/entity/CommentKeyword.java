package com.mintjams.social_insight.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity(name = "comment_keyword")
@Getter
@Setter
@NoArgsConstructor
public class CommentKeyword {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //채널id(외래키)
    @ManyToOne
    @JoinColumn(name = "channel_id", referencedColumnName = "channelId", nullable = false)
    private Channel channel;

    //컨텐트id
    @ManyToOne
    @JoinColumn(name = "content_id", referencedColumnName = "contentId", nullable = false)
    private Content content;

    @Column(unique = true)
    private String commentKey;

    @Column
    private int found;


    // keyword와 score를 받는 생성자
    public CommentKeyword(String keyword, int found) {
        this.commentKey = keyword;
        this.found = found;
    }

    public void addScore(int found) {
        this.found += found;
    }



}


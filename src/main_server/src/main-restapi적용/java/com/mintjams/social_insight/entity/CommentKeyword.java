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
    @JoinColumn(name = "channel_id") //String
    private Channel channel;

    //컨텐트id
    @ManyToOne
    @JoinColumn(name = "content_id") //String
    private Content content;

    @Column
    private String commentKey;

    @Column
    private int found;


}


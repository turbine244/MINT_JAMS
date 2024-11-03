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

    @Column(unique = true)
    private String commentKeyword;

    @Column
    private int score;

//    @ManyToOne
//    @JoinColumn(name = "video_id", nullable = false)
//    private Video video;

    // keyword와 score를 받는 생성자
    public CommentKeyword(String keyword, int score) {
        this.commentKeyword = keyword;
        this.score = score;
    }

    public void addScore(int score) {
        this.score += score;
    }


//    public static CommentKeyword toCommentkeyword
//
//


}


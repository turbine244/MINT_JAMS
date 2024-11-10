package com.mintjams.social_insight.dto;

import com.mintjams.social_insight.entity.CommentKeyword;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CommentKeywordDTO {
   // private Long contentId;
    private String commentKeyword;
    private int score;

    public CommentKeywordDTO(String keyword, int score) {
        this.commentKeyword = keyword;
        this.score = score;
    }

    public static CommentKeywordDTO toCommentKeywordDTO(CommentKeyword commentKeyword) {
        CommentKeywordDTO commentKeywordDTO = new CommentKeywordDTO();
        commentKeywordDTO.setCommentKeyword(commentKeyword.getCommentKeyword());
        commentKeywordDTO.setScore(commentKeyword.getScore());
        return commentKeywordDTO;
    }
}

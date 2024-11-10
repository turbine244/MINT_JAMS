package com.mintjams.social_insight.dto;


import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class KeywordDTO {
    private ContentKeywordDTO contentKeyword;
    private List<CommentKeywordDTO> commentKeywords;

    // Constructors
    public KeywordDTO(ContentKeywordDTO contentKeyword, List<CommentKeywordDTO> commentKeywords) {
        this.contentKeyword = contentKeyword;
        this.commentKeywords = commentKeywords;
    }

//    // Getters and Setters
//    public ContentKeywordDTO getContentKeyword() { return contentKeyword; }
//    public void setContentKeyword(ContentKeywordDTO contentKeyword) { this.contentKeyword = contentKeyword; }
//
//    public List<CommentKeywordDTO> getCommentKeywords() { return commentKeywords; }
//    public void setCommentKeywords(List<CommentKeywordDTO> commentKeywords) { this.commentKeywords = commentKeywords; }


    }

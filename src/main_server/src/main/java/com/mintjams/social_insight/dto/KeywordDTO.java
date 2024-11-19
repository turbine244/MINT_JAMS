package com.mintjams.social_insight.dto;


import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class KeywordDTO {

    private List<String> keyList;
    private List<Integer> foundList;
    private Double sentiment;

    public KeywordDTO(List<String> keyList, List<Integer> foundList, Double sentiment) {
        this.keyList = keyList;
        this.foundList = foundList;
        this.sentiment = sentiment;
    }

}

package com.mintjams.social_insight.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class WordCloudDTO {
    private List<String> keyList;
    private List<Long> foundList;

    public WordCloudDTO(List<String> keyList, List<Long> foundList) {
        this.keyList = keyList;
        this.foundList = foundList;
    }
}

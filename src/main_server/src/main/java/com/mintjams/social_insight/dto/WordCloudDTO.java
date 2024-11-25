package com.mintjams.social_insight.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class WordCloudDTO {
    private List<String> keyList;
    private List<Long> foundList;

}

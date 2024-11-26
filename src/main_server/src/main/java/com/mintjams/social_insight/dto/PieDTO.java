package com.mintjams.social_insight.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PieDTO {

    private String contentId;
    private List<String> keyList;
    private List<Integer> foundList;
    private Double sentiment;


}

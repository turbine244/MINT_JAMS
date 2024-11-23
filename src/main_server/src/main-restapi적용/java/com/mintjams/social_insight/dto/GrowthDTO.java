package com.mintjams.social_insight.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class GrowthDTO {

    private List<Integer> growth_content;
    private List<Integer> growth_comment;

}

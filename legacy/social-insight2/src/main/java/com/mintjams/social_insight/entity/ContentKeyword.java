package com.mintjams.social_insight.entity;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity(name = "content_keyword")
@Getter
@Setter
@NoArgsConstructor
public class ContentKeyword {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) //auto_increment
    private Long id;
//
//    @Column(unique = true)
//    private String memberEmail;

}



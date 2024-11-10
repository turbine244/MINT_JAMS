package com.mintjams.social_insight.entity;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity(name = "channel")
@Getter
@Setter
@NoArgsConstructor
public class Channel {

    @Id
    @Column(unique = true)
    private String channelId;

}

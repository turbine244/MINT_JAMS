package com.mintjams.social_insight.entity;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;

@Entity(name = "channel")
@Getter
@Setter
@NoArgsConstructor
public class Channel {

    @Id
    @Column(unique = true)
    private String channelId;

    //컨텐츠 수
    @Column
    private Integer contentNum;

    //앵커 사이트 최초 발견 시기
    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    //앵커 사이트 마지막 갱신
    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Column
    private Integer AnchorNum;


}

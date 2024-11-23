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
    private Integer anchorNum = 0;

//    @Column
//    private Double sentiment = 0.0;


    // 엔티티가 처음 저장될 때 호출되는 메서드
    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    // 엔티티가 수정될 때 호출되는 메서드
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now(); // 마지막 수정 시간 갱신
    }


}

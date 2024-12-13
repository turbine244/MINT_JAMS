package com.mintjams.social_insight;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SocialInsightApplication {

	public static void main(String[] args) {
		SpringApplication.run(SocialInsightApplication.class, args);
	}

}

package com.mintjams.social_insight.service;

import com.google.gson.*;
import com.mintjams.social_insight.dto.VideoDataDTO;
import com.mintjams.social_insight.repository.KeywordRepository;
import com.mintjams.social_insight.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class YouTubeService {

    private final VideoRepository videoRepository;
    private final KeywordRepository keywordRepository;

    // 메서드: YouTube API로부터 동영상 데이터를 가져오고 DTO에 저장
    public VideoDataDTO getVideoData(String videoId, String apiKey, String flaskUrl) throws Exception {
        // 동영상 세부 정보 URL
        String videoInfoUrl = "https://www.googleapis.com/youtube/v3/videos?part=snippet,statistics&id=" + videoId + "&key=" + apiKey;
        // 댓글 정보 URL
        String commentUrl = "https://www.googleapis.com/youtube/v3/commentThreads?part=snippet&videoId=" + videoId + "&maxResults=100&key=" + apiKey;

        CloseableHttpClient client = HttpClients.createDefault();
        VideoDataDTO videoDataDTO = new VideoDataDTO();

        try {
            // 동영상 세부 정보 가져오기
            HttpGet videoRequest = new HttpGet(videoInfoUrl);
            HttpResponse videoResponse = client.execute(videoRequest);
            BufferedReader videoReader = new BufferedReader(new InputStreamReader(videoResponse.getEntity().getContent(), StandardCharsets.UTF_8));
            StringBuilder videoJsonResponse = new StringBuilder();
            String videoLine;

            while ((videoLine = videoReader.readLine()) != null) {
                videoJsonResponse.append(videoLine);
            }

            videoReader.close();

            // JSON 파싱
            JsonElement videoJsonElement = JsonParser.parseString(videoJsonResponse.toString());
            JsonObject videoJsonObject = videoJsonElement.getAsJsonObject();
            JsonArray videoItems = videoJsonObject.getAsJsonArray("items");

            // 동영상 정보 DTO에 저장
            if (videoItems.size() > 0) {
                JsonObject videoSnippet = videoItems.get(0).getAsJsonObject().getAsJsonObject("snippet");
                JsonObject videoStatistics = videoItems.get(0).getAsJsonObject().getAsJsonObject("statistics");

                String title = videoSnippet.get("title").getAsString();
                String description = videoSnippet.get("description").getAsString();
                String publishedAt = videoSnippet.get("publishedAt").getAsString(); // 업로드 날짜
                String viewCount = videoStatistics.get("viewCount").getAsString(); // 조회수
                String likeCount = videoStatistics.get("likeCount").getAsString(); // 좋아요 수

                // DTO에 데이터 설정
                videoDataDTO.setVideoId(videoId);
                videoDataDTO.setTitle(title);
                videoDataDTO.setDescription(description);
                videoDataDTO.setPublishedAt(publishedAt);
                videoDataDTO.setViewCount(viewCount);
                videoDataDTO.setLikeCount(likeCount);

            } else {
                System.out.println("No video found.");
                return null;
            }

            // 댓글 정보 가져오기
            JsonArray commentsArray = new JsonArray();
            String nextPageToken = null;
            int totalComments = 0; // 댓글 개수 카운트

            do {
                HttpGet commentRequest = new HttpGet(commentUrl + (nextPageToken != null ? "&pageToken=" + nextPageToken : ""));
                HttpResponse commentResponse = client.execute(commentRequest);
                BufferedReader commentReader = new BufferedReader(new InputStreamReader(commentResponse.getEntity().getContent(), "UTF-8"));
                StringBuilder commentJsonResponse = new StringBuilder();
                String commentLine;

                while ((commentLine = commentReader.readLine()) != null) {
                    commentJsonResponse.append(commentLine);
                }
                commentReader.close();

                // JSON 파싱
                JsonElement commentJsonElement = JsonParser.parseString(commentJsonResponse.toString());
                JsonObject commentJsonObject = commentJsonElement.getAsJsonObject();

                if (commentJsonObject.has("items") && !commentJsonObject.get("items").isJsonNull()) {
                    JsonArray commentItems = commentJsonObject.getAsJsonArray("items");

                    // 댓글 추가
                    for (JsonElement item : commentItems) {
                        if (totalComments >= 10) break; // 10개 이상이면 중단
                        JsonObject snippet = item.getAsJsonObject().getAsJsonObject("snippet")
                                .getAsJsonObject("topLevelComment").getAsJsonObject("snippet");
                        String comment = snippet.get("textDisplay").getAsString();
                        commentsArray.add(comment);
                        totalComments++; // 댓글 수 증가
                    }

                    // 다음 페이지 토큰 설정
                    nextPageToken = commentJsonObject.has("nextPageToken")
                            ? commentJsonObject.get("nextPageToken").getAsString()
                            : null;
                } else {
                    nextPageToken = null;
                }
            } while (nextPageToken != null && totalComments < 100); // 댓글 수가 100개 미만일 때 계속 반복

            // 동영상 정보와 댓글을 JSON 형태로 Flask 서버에 보낼 준비
            JsonObject outputJson = new JsonObject();
            outputJson.addProperty("title", videoDataDTO.getTitle());
            outputJson.addProperty("description", videoDataDTO.getDescription());
            outputJson.addProperty("publishedAt", videoDataDTO.getPublishedAt());
            outputJson.addProperty("viewCount", videoDataDTO.getViewCount());
            outputJson.addProperty("likeCount", videoDataDTO.getLikeCount());
            outputJson.add("comments", commentsArray);

            // Flask 서버로 동영상 정보 및 댓글 전송
            videoDataDTO = sendJsonToFlaskServer(videoDataDTO, flaskUrl, outputJson);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            client.close();
        }


        return videoDataDTO;

    }

    // Flask 서버로 동영상 데이터를 전송하고 받는 메서드
    private VideoDataDTO sendJsonToFlaskServer(VideoDataDTO videoDataDTO, String flaskUrl, JsonObject outputJson) {

        System.out.println("Sending JSON to Flask: " + outputJson.toString()); // 전송할 JSON 출력

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(flaskUrl);
            StringEntity entity = new StringEntity(outputJson.toString(), ContentType.APPLICATION_JSON);
            httpPost.setEntity(entity);

            // Flask 서버로 POST 요청 보내기
            HttpResponse response = client.execute(httpPost);

            System.out.println("Flask 서버로 데이터 보내는 중");

            // HTTP 응답 코드 확인
            int statusCode = response.getStatusLine().getStatusCode();
            String responseBody = EntityUtils.toString(response.getEntity(), "UTF-8");
            System.out.println("Response from Flask: " + responseBody);

            if (statusCode != 200) {
                String errorMessage = "Flask 서버와의 통신 중 오류 발생: " + statusCode + ". 응답 본문: " + responseBody;
                System.out.println(errorMessage);
                throw new IOException(errorMessage);
            } else {
                System.out.println("Flask 서버와의 통신 성공: " + statusCode);
            }

            // 응답 본문을 읽어와서 JSON으로 파싱
            JsonObject flaskResponse = JsonParser.parseString(responseBody).getAsJsonObject();

            // Flask 응답 데이터를 VideoDataDTO에 저장
            videoDataDTO.setTitleKeyword(flaskResponse.get("title_keyword").getAsString());
            videoDataDTO.setDescriptionKeyword(flaskResponse.get("description_keyword").getAsString());
            videoDataDTO.setCommentKeyword(flaskResponse.get("comment_keyword").getAsString());

        } catch (Exception e) {
            e.printStackTrace();
        }

        return videoDataDTO;
    }
}

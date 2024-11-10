package com.mintjams.social_insight.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.*;
import com.mintjams.social_insight.dto.*;
import com.mintjams.social_insight.entity.CommentKeyword;
import com.mintjams.social_insight.repository.CommentKeywordRepository;
import com.mintjams.social_insight.repository.ContentKeywordRepository;
import lombok.RequiredArgsConstructor;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class YouTubeService {

    @Autowired
    private final CommentKeywordRepository commentKeywordRepository;
    @Autowired
    private final ContentKeywordRepository contentKeywordRepository;


    //채널데이터 가져오기
    public ChannelDTO getChannelData(String channelTitle, String apiKey) {
        ChannelDTO channelDTO = new ChannelDTO();

        // 채널 ID 가져오기 URL
        String searchChannelUrl = "https://www.googleapis.com/youtube/v3/search?part=snippet&type=channel&q="
                + channelTitle + "&key=" + apiKey;

        try {
            CloseableHttpClient client = HttpClients.createDefault();

            // 채널 ID 가져오기
            HttpGet channelRequest = new HttpGet(searchChannelUrl);
            HttpResponse channelResponse = client.execute(channelRequest);
            BufferedReader channelReader = new BufferedReader(
                    new InputStreamReader(channelResponse.getEntity().getContent(), "UTF-8"));
            StringBuilder channelJsonResponse = new StringBuilder();
            String channelLine;

            while ((channelLine = channelReader.readLine()) != null) {
                channelJsonResponse.append(channelLine);
            }
            channelReader.close();

            JsonElement channelJsonElement = JsonParser.parseString(channelJsonResponse.toString());
            JsonObject channelJsonObject = channelJsonElement.getAsJsonObject();
            JsonArray channelItems = channelJsonObject.getAsJsonArray("items");

            // 채널 ID 추출
            if (channelItems.size() == 0) {
                System.out.println("No channel found.");
                return null;
            }
            String channelId = channelItems.get(0).getAsJsonObject().getAsJsonObject("id").get("channelId").getAsString();

            // 채널 상세 정보 가져오기 URL
            String channelInfoUrl = "https://www.googleapis.com/youtube/v3/channels?part=snippet,statistics&id="
                    + channelId + "&key=" + apiKey;

            HttpGet channelInfoRequest = new HttpGet(channelInfoUrl);
            HttpResponse channelInfoResponse = client.execute(channelInfoRequest);
            BufferedReader channelInfoReader = new BufferedReader(
                    new InputStreamReader(channelInfoResponse.getEntity().getContent(), "UTF-8"));
            StringBuilder channelInfoJsonResponse = new StringBuilder();
            String channelInfoLine;

            while ((channelInfoLine = channelInfoReader.readLine()) != null) {
                channelInfoJsonResponse.append(channelInfoLine);
            }
            channelInfoReader.close();

            JsonElement channelInfoJsonElement = JsonParser.parseString(channelInfoJsonResponse.toString());
            JsonObject channelInfoJsonObject = channelInfoJsonElement.getAsJsonObject();
            JsonArray channelInfoItems = channelInfoJsonObject.getAsJsonArray("items");

            JsonObject channelOutputJson = new JsonObject();
            if (channelInfoItems.size() > 0) {
                JsonObject channelSnippet = channelInfoItems.get(0).getAsJsonObject().getAsJsonObject("snippet");
                JsonObject channelStatistics = channelInfoItems.get(0).getAsJsonObject().getAsJsonObject("statistics");

                channelTitle = channelSnippet.get("title").getAsString(); // 채널명
                String channelUrl = "https://www.youtube.com/channel/" + channelId; // 채널 메인 주소
                String publishedAtFull = channelSnippet.get("publishedAt").getAsString(); // 채널 개설일 전체
                String publishedAt = publishedAtFull.split("T")[0]; // 채널 개설일 날짜만

                String subscriberCount = channelStatistics.has("subscriberCount")
                        ? channelStatistics.get("subscriberCount").getAsString()
                        : "Hidden"; // 구독자 수 (비공개인 경우 'Hidden')
                String videoCount = channelStatistics.get("videoCount").getAsString(); // 동영상 수

                //
                //videoDataDTO.setVideoId(videoId);

                // DTO에 데이터 설정
                channelDTO.setChannelId(channelId);

                channelDTO.setChannelTitle(channelTitle);
                channelDTO.setChannelUrl(channelUrl);
                channelDTO.setCreatedAt(publishedAt);
                channelDTO.setSubscriberCount(subscriberCount);
                channelDTO.setVideoCount(videoCount);

            }

            client.close();
        } catch (Exception e) {
            e.printStackTrace();
        }


        return channelDTO;
    }


    //키워드 데이터 DB에 저장
    public void setKeywordData(String channelId, String apiKey) {

        KeywordDTO keywordDTO = new KeywordDTO();

        try {
            CloseableHttpClient client = HttpClients.createDefault();

            // 1. 채널의 최신 동영상 가져오기
            String latestVideoUrl = "https://www.googleapis.com/youtube/v3/search?part=snippet&channelId="
                    + channelId + "&order=date&maxResults=1&key=" + apiKey;

            HttpGet latestVideoRequest = new HttpGet(latestVideoUrl);
            HttpResponse latestVideoResponse = client.execute(latestVideoRequest);
            BufferedReader videoReader = new BufferedReader(
                    new InputStreamReader(latestVideoResponse.getEntity().getContent(), "UTF-8"));
            StringBuilder latestVideoJsonResponse = new StringBuilder();
            String videoLine;

            while ((videoLine = videoReader.readLine()) != null) {
                latestVideoJsonResponse.append(videoLine);
            }
            videoReader.close();

            JsonElement latestVideoJsonElement = JsonParser.parseString(latestVideoJsonResponse.toString());
            JsonObject latestVideoJsonObject = latestVideoJsonElement.getAsJsonObject();
            JsonArray videoItems = latestVideoJsonObject.getAsJsonArray("items");

            if (videoItems.size() == 0) {
                System.out.println("No videos found.");
            }
            String videoId = videoItems.get(0).getAsJsonObject().getAsJsonObject("id").get("videoId").getAsString();

            // 2. 동영상 세부 정보 가져오기
            String videoInfoUrl = "https://www.googleapis.com/youtube/v3/videos?part=snippet,statistics&id="
                    + videoId + "&key=" + apiKey;

            HttpGet videoRequest = new HttpGet(videoInfoUrl);
            HttpResponse videoResponse = client.execute(videoRequest);
            BufferedReader videoInfoReader = new BufferedReader(
                    new InputStreamReader(videoResponse.getEntity().getContent(), "UTF-8"));
            StringBuilder videoInfoJsonResponse = new StringBuilder();

            while ((videoLine = videoInfoReader.readLine()) != null) {
                videoInfoJsonResponse.append(videoLine);
            }
            videoInfoReader.close();

            JsonElement videoJsonElement = JsonParser.parseString(videoInfoJsonResponse.toString());
            JsonObject videoJsonObject = videoJsonElement.getAsJsonObject();
            JsonArray videoInfoItems = videoJsonObject.getAsJsonArray("items");

            JsonObject outputJson = new JsonObject();
            if (videoInfoItems.size() > 0) {
                JsonObject videoSnippet = videoInfoItems.get(0).getAsJsonObject().getAsJsonObject("snippet");
                JsonObject videoStatistics = videoInfoItems.get(0).getAsJsonObject().getAsJsonObject("statistics");

                String title = videoSnippet.get("title").getAsString();
                String description = videoSnippet.get("description").getAsString();
                String publishedAt = videoSnippet.get("publishedAt").getAsString();
                String viewCount = videoStatistics.get("viewCount").getAsString();
                String likeCount = videoStatistics.get("likeCount").getAsString();

                //Json파일에 저장
                outputJson.addProperty("title", title);
                outputJson.addProperty("description", description);
                outputJson.addProperty("publishedAt", publishedAt);
                outputJson.addProperty("viewCount", viewCount);
                outputJson.addProperty("likeCount", likeCount);
            }

            // 3. 댓글 가져오기
            String commentUrl = "https://www.googleapis.com/youtube/v3/commentThreads?part=snippet&videoId=" + videoId
                    + "&order=time&maxResults=100&key=" + apiKey;

            HttpGet commentRequest = new HttpGet(commentUrl);
            HttpResponse commentResponse = client.execute(commentRequest);
            BufferedReader commentReader = new BufferedReader(
                    new InputStreamReader(commentResponse.getEntity().getContent(), "UTF-8"));
            StringBuilder commentJsonResponse = new StringBuilder();
            String line;

            while ((line = commentReader.readLine()) != null) {
                commentJsonResponse.append(line);
            }
            commentReader.close();

            JsonElement commentElement = JsonParser.parseString(commentJsonResponse.toString());
            JsonObject commentObject = commentElement.getAsJsonObject();
            JsonArray comments = commentObject.getAsJsonArray("items");


            //Json파일에 저장
            //JsonObject outputJson = new JsonObject();
            // 4. JSON 파일로 저장 (간소화된 구조)
            try (FileWriter fileWriter = new FileWriter("comments.json")) {
                JsonArray commentsArray = new JsonArray();

                for (JsonElement item : comments) {
                    String textOriginal = item.getAsJsonObject().getAsJsonObject("snippet")
                            .getAsJsonObject("topLevelComment").getAsJsonObject("snippet").get("textOriginal").getAsString();
                    commentsArray.add(textOriginal); // textOriginal 값만 추가
                }

                // 최종 결과를 포함하는 JSON 객체
                outputJson.add("comments", commentsArray);

                // JSON을 파일에 작성
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                gson.toJson(outputJson, fileWriter);
                System.out.println("Comments saved to comments.json");
            }


            // 동영상 정보와 댓글을 JSON 형태로 Flask 서버에 보낼 준비 완료
            // Flask 서버로 동영상 정보 및 댓글 전송DB 저장
            JsonNode flaskResponse = sendJsonToFlaskServer(outputJson);


            //System.out.println("JSON file created at: " + jsonFile.getAbsolutePath());

            //content를 db에 저장
            /*
             *
             * 구현 전
             *
             * */


            //comment를 db에 저장 -> 다시 구현
            /*
             *
             * 구현 전
             *
             * */


//            // comment_keyword 배열 가져오기
//            JsonNode commentKeywordArray = flaskResponse.path("comment_keyword");
//
//            // 배열이 존재하고, 배열 내 각 객체를 순회
//            if (commentKeywordArray.isArray()) {
//                for (JsonNode objNode : commentKeywordArray) {
//                    String keyword = objNode.path("keyword").asText();
//                    int found = objNode.path("score").asInt();
//
//                    // 키워드가 이미 존재하는지 확인
//                    commentKeywordRepository.findByCommentKey(keyword).ifPresentOrElse(
//                            existingKeyword -> {
//                                // 키워드가 이미 존재하면 score 누적
//                                existingKeyword.addScore(found);
//                                commentKeywordRepository.save(existingKeyword);
//                            },
//                            () -> {
//                                // 키워드가 없으면 새로 생성하여 저장
//                                CommentKeyword newKeyword = new CommentKeyword(keyword, found);
//                                commentKeywordRepository.save(newKeyword);
//                            }
//                    );
//                }
//            }


            client.close();
        } catch (Exception e) {
            e.printStackTrace();
        }


    }

    // Flask 서버로 동영상 데이터를 전송하고 받는 메서드
    private JsonNode sendJsonToFlaskServer(JsonObject outputJson) {

        String flaskUrl = "http://localhost:5000/respond";
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

            // 응답 본문을 읽어와서 JSON으로 파싱 -> Jackson 라이브러리를 활용 코드로 변경
            ObjectMapper objectMapper = new ObjectMapper();
            //JsonObject flaskResponse = JsonParser.parseString(responseBody).getAsJsonObject();
            return objectMapper.readTree(responseBody);


        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;

    }

    //워드클라우드 그래프 - 모든 키워드 상위 100개
    public KeywordDTO getWordCloudData(String channelId) {

// Content 및 CommentKeyword 데이터를 각각 조회
        List<Object[]> contentData = contentKeywordRepository.findContentKeysAndFoundByChannelId(channelId);
        List<Object[]> commentData = commentKeywordRepository.findCommentKeysAndFoundByChannelId(channelId);

        // 모든 데이터를 하나의 리스트로 병합
        List<Object[]> combinedData = new ArrayList<>();
        combinedData.addAll(contentData);
        combinedData.addAll(commentData);

        // found 값 기준으로 내림차순 정렬
        combinedData.sort((o1, o2) -> Integer.compare((Integer) o2[1], (Integer) o1[1]));

        // 상위 100개의 데이터만 선택
        List<String> keyList = new ArrayList<>();
        List<Integer> foundList = new ArrayList<>();

        for (int i = 0; i < Math.min(100, combinedData.size()); i++) {
            keyList.add((String) combinedData.get(i)[0]);  // keyword
            foundList.add((Integer) combinedData.get(i)[1]); // found
        }

        // KeywordDTO에 리스트 설정
        KeywordDTO keywordDTO = new KeywordDTO();
        keywordDTO.setKeyList(keyList);
        keywordDTO.setFoundList(foundList);

        return keywordDTO;
    }

    //주제 키워드 랭킹 - 본문 키워드 상위 10개
    public KeywordDTO getRankingData(String channelId) {

        // 상위 10개 데이터를 위한 PageRequest 생성
        PageRequest pageRequest = PageRequest.of(0, 10);
        List<Object[]> contentData = contentKeywordRepository
                .findTop10ContentKeysAndFoundByChannelIdOrderByFoundDesc(channelId, pageRequest);

        // 각각의 데이터를 저장할 리스트 초기화
        List<String> contentKeyList = new ArrayList<>();
        List<Integer> foundList = new ArrayList<>();

        // 데이터를 각각의 리스트에 추가
        for (Object[] row : contentData) {
            contentKeyList.add((String) row[0]);  // contentKey
            foundList.add((Integer) row[1]);      // found
        }

        // KeywordDTO에 리스트 설정
        KeywordDTO keywordDTO = new KeywordDTO();
        keywordDTO.setKeyList(contentKeyList);
        keywordDTO.setFoundList(foundList);

        return keywordDTO;
    }

    //파이 차트 - 댓글 키워드 (동영상 당 8개)
    public KeywordDTO getPieData(String channelId) {

        String contentId = "";

        List<Object[]> commentData = commentKeywordRepository
                .findTop8CommentKeysAndFoundByChannelIdAndContentIdOrderByFoundDesc(channelId, contentId);

        // 각각의 데이터를 저장할 리스트 초기화
        List<String> keyList = new ArrayList<>();
        List<Integer> foundList = new ArrayList<>();

        // 데이터를 각각의 리스트에 추가
        for (Object[] row : commentData) {
            keyList.add((String) row[0]);      // commentKey
            foundList.add((Integer) row[1]);   // found
        }

        // KeywordDTO에 리스트 설정
        KeywordDTO keywordDTO = new KeywordDTO();
        keywordDTO.setKeyList(keyList);
        keywordDTO.setFoundList(foundList);

        return keywordDTO;
    }

    public Object getGrowthData(String channelId) {

        List<Integer> growthContent  = new ArrayList<>();
        List<Integer> growthComment  = new ArrayList<>();



        return null;
    }



}
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

    // 채널데이터 가져오기
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
            String channelId = channelItems.get(0).getAsJsonObject().getAsJsonObject("id").get("channelId")
                    .getAsString();

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
                // videoDataDTO.setVideoId(videoId);

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

    // 분석 소요 파악
    public long checkUpdate(String channelId, String apiKey) {
        // 댓글 수 변화 측정

        // 마지막 검사 단위로부터 100개 추가됐는지 측정

        // 지금은 그냥 최근 영상 1개의 본문과 최근 100개 댓글 무조건 검사함
        // 나중엔 큐에 넣어서 하겠지
        String idContent = getLatestVideoId(channelId, apiKey);
        JsonObject inputJson;

        // 본문 분석
        inputJson = get_data_content(channelId, apiKey, idContent);
        setKeywordData(channelId, apiKey, idContent, false, inputJson);

        // 댓글 분석
        inputJson = get_data_comment(channelId, apiKey, idContent, 0);
        setKeywordData(channelId, apiKey, idContent, true, inputJson);

        return 0;
    }

    // 본문 입력 json 뽑기
    public JsonObject get_data_content(String channelId, String apiKey, String idContent) {
        // YouTube API URL 설정 (비디오 정보 가져오기)
        String videoUrl = "https://www.googleapis.com/youtube/v3/videos?part=snippet&id="
                + idContent + "&key=" + apiKey;

        // HttpClient 생성
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            // 비디오 정보 요청
            HttpGet videoRequest = new HttpGet(videoUrl);
            HttpResponse videoResponse = client.execute(videoRequest);
            BufferedReader videoReader = new BufferedReader(
                    new InputStreamReader(videoResponse.getEntity().getContent(), "UTF-8"));
            StringBuilder videoJsonResponse = new StringBuilder();
            String line;

            // 응답 읽기
            while ((line = videoReader.readLine()) != null) {
                videoJsonResponse.append(line);
            }
            videoReader.close();

            // JSON 응답 파싱
            JsonObject videoJsonObject = JsonParser.parseString(videoJsonResponse.toString()).getAsJsonObject();
            JsonArray videoItems = videoJsonObject.getAsJsonArray("items");

            if (videoItems.size() > 0) {
                JsonObject videoSnippet = videoItems.get(0).getAsJsonObject().getAsJsonObject("snippet");
                String title = videoSnippet.get("title").getAsString();
                String description = videoSnippet.get("description").getAsString();

                // JSON 형태로 출력 (배열 형식으로 title과 description 추가)
                JsonArray dataArray = new JsonArray();
                dataArray.add(title);
                dataArray.add(description);

                JsonObject outputJson = new JsonObject();
                outputJson.add("data", dataArray);

                // 출력
                System.out.println(outputJson.toString());

                // 반환
                return outputJson;
            } else {
                System.out.println("No video found with the given ID.");
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // 댓글 입력 json 뽑기
    public JsonObject get_data_comment(String channelId, String apiKey, String idContent, long offset) {
        // YouTube API URL 설정 (최신 댓글 100개 가져오기)
        String commentUrl = "https://www.googleapis.com/youtube/v3/commentThreads?part=snippet&videoId="
                + idContent + "&order=time&maxResults=100&key=" + apiKey;

        // HttpClient 생성
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            // 댓글 요청
            HttpGet commentRequest = new HttpGet(commentUrl);
            HttpResponse commentResponse = client.execute(commentRequest);
            BufferedReader commentReader = new BufferedReader(
                    new InputStreamReader(commentResponse.getEntity().getContent(), "UTF-8"));
            StringBuilder commentJsonResponse = new StringBuilder();
            String line;

            // 댓글 응답 읽기
            while ((line = commentReader.readLine()) != null) {
                commentJsonResponse.append(line);
            }
            commentReader.close();

            // JSON 응답 파싱
            JsonElement commentElement = JsonParser.parseString(commentJsonResponse.toString());
            JsonObject commentObject = commentElement.getAsJsonObject();
            JsonArray comments = commentObject.getAsJsonArray("items");

            // 댓글 텍스트만 추출하여 배열로 저장
            ArrayList<String> textOriginalList = new ArrayList<>();
            for (JsonElement item : comments) {
                String textOriginal = item.getAsJsonObject().getAsJsonObject("snippet")
                        .getAsJsonObject("topLevelComment").getAsJsonObject("snippet")
                        .get("textOriginal").getAsString();
                textOriginalList.add(textOriginal); // 텍스트만 배열에 추가
            }

            // JSON 형태로 출력 (속성명을 'data'로 변경)
            JsonObject outputJson = new JsonObject();
            outputJson.add("data", new JsonArray());
            for (String text : textOriginalList) {
                outputJson.getAsJsonArray("data").add(new JsonPrimitive(text));
            }

            // 출력
            System.out.println(outputJson.toString());

            // 반환
            return outputJson;

        } catch (IOException e) {
            // IOException 처리
            e.printStackTrace();
            return null;
        }
    }

    // 키워드 데이터 DB에 저장
    public void setKeywordData(String channelId, String apiKey, String idContent, boolean isComment,
            JsonObject inputJson) {

        KeywordDTO keywordDTO = new KeywordDTO();

        // 동영상 정보와 댓글을 JSON 형태로 Flask 서버에 보낼 준비 완료
        // Flask 서버로 동영상 정보 및 댓글 전송DB 저장
        JsonNode flaskResponse = sendJsonToFlaskServer(inputJson);

        // System.out.println("JSON file created at: " + jsonFile.getAbsolutePath());

        /// !!! 박근원
        /// 파라미터 추가
        /// idContent : 비디오 id
        /// isComment : 코멘트 분석이면 true, 콘텐트 분석이면 false
        /// inputJson : 양식은 무조건 "data" 산하의 텍스트 배열

        // content를 db에 저장
        /*
         *
         * 구현 전
         *
         */

        // comment를 db에 저장 -> 다시 구현
        /*
         *
         * 구현 전
         *
         */

        // // comment_keyword 배열 가져오기
        // JsonNode commentKeywordArray = flaskResponse.path("comment_keyword");
        //
        // // 배열이 존재하고, 배열 내 각 객체를 순회
        // if (commentKeywordArray.isArray()) {
        // for (JsonNode objNode : commentKeywordArray) {
        // String keyword = objNode.path("keyword").asText();
        // int found = objNode.path("score").asInt();
        //
        // // 키워드가 이미 존재하는지 확인
        // commentKeywordRepository.findByCommentKey(keyword).ifPresentOrElse(
        // existingKeyword -> {
        // // 키워드가 이미 존재하면 score 누적
        // existingKeyword.addScore(found);
        // commentKeywordRepository.save(existingKeyword);
        // },
        // () -> {
        // // 키워드가 없으면 새로 생성하여 저장
        // CommentKeyword newKeyword = new CommentKeyword(keyword, found);
        // commentKeywordRepository.save(newKeyword);
        // }
        // );
        // }
        // }
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
            // JsonObject flaskResponse =
            // JsonParser.parseString(responseBody).getAsJsonObject();
            return objectMapper.readTree(responseBody);

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;

    }

    // 워드클라우드 그래프 - 모든 키워드 상위 100개
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
            keyList.add((String) combinedData.get(i)[0]); // keyword
            foundList.add((Integer) combinedData.get(i)[1]); // found
        }

        // KeywordDTO에 리스트 설정
        KeywordDTO keywordDTO = new KeywordDTO();
        keywordDTO.setKeyList(keyList);
        keywordDTO.setFoundList(foundList);

        return keywordDTO;
    }

    // 주제 키워드 랭킹 - 본문 키워드 상위 10개
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
            contentKeyList.add((String) row[0]); // contentKey
            foundList.add((Integer) row[1]); // found
        }

        // KeywordDTO에 리스트 설정
        KeywordDTO keywordDTO = new KeywordDTO();
        keywordDTO.setKeyList(contentKeyList);
        keywordDTO.setFoundList(foundList);

        return keywordDTO;
    }

    // 파이 차트 - 댓글 키워드 (동영상 당 8개)
    public KeywordDTO getPieData(String channelId) {

        String contentId = "";

        List<Object[]> commentData = commentKeywordRepository
                .findTop8CommentKeysAndFoundByChannelIdAndContentIdOrderByFoundDesc(channelId, contentId);

        // 각각의 데이터를 저장할 리스트 초기화
        List<String> keyList = new ArrayList<>();
        List<Integer> foundList = new ArrayList<>();

        // 데이터를 각각의 리스트에 추가
        for (Object[] row : commentData) {
            keyList.add((String) row[0]); // commentKey
            foundList.add((Integer) row[1]); // found
        }

        // KeywordDTO에 리스트 설정
        KeywordDTO keywordDTO = new KeywordDTO();
        keywordDTO.setKeyList(keyList);
        keywordDTO.setFoundList(foundList);

        return keywordDTO;
    }

    public Object getGrowthData(String channelId) {

        List<Integer> growthContent = new ArrayList<>();
        List<Integer> growthComment = new ArrayList<>();

        return null;
    }

    ////// 임시임시임시임시

    // ! 임시함수 : 최신 영상 ID 가져오기
    public String getLatestVideoId(String channelId, String apiKey) {
        // YouTube API URL 설정 (최신 영상 가져오기)
        String videoUrl = "https://www.googleapis.com/youtube/v3/search?part=snippet&channelId="
                + channelId + "&order=date&maxResults=1&key=" + apiKey;

        // HttpClient 생성
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            // 비디오 정보 요청
            HttpGet videoRequest = new HttpGet(videoUrl);
            HttpResponse videoResponse = client.execute(videoRequest);
            BufferedReader videoReader = new BufferedReader(
                    new InputStreamReader(videoResponse.getEntity().getContent(), "UTF-8"));
            StringBuilder videoJsonResponse = new StringBuilder();
            String line;

            // 응답 읽기
            while ((line = videoReader.readLine()) != null) {
                videoJsonResponse.append(line);
            }
            videoReader.close();

            // JSON 응답 파싱
            JsonObject videoJsonObject = JsonParser.parseString(videoJsonResponse.toString()).getAsJsonObject();
            JsonArray videoItems = videoJsonObject.getAsJsonArray("items");

            if (videoItems.size() > 0) {
                // 가장 최신 비디오 ID 반환 (문자열 형식)
                return videoItems.get(0).getAsJsonObject().getAsJsonObject("id").get("videoId").getAsString();
            } else {
                System.out.println("No videos found.");
                return null;
            }

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

}
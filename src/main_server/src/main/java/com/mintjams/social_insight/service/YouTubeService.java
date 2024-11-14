package com.mintjams.social_insight.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.*;
import com.mintjams.social_insight.dto.*;
import com.mintjams.social_insight.entity.Channel;
import com.mintjams.social_insight.entity.CommentKeyword;
import com.mintjams.social_insight.entity.Content;
import com.mintjams.social_insight.entity.ContentKeyword;
import com.mintjams.social_insight.repository.*;
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

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class YouTubeService {

    private final CommentKeywordRepository commentKeywordRepository;
    private final ContentKeywordRepository contentKeywordRepository;
    private final ChannelRepository channelRepository;
    private final ContentRepository contentRepository;

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

                String _channelTitle = channelSnippet.get("title").getAsString(); // 채널명
                String _channelUrl = "https://www.youtube.com/channel/" + channelId; // 채널 메인 주소
                String _publishedAtFull = channelSnippet.get("publishedAt").getAsString(); // 채널 개설일 전체
                String _publishedAt = _publishedAtFull.split("T")[0]; // 채널 개설일 날짜만

                String _subscriberCount = channelStatistics.has("subscriberCount")
                        ? channelStatistics.get("subscriberCount").getAsString()
                        : "Hidden"; // 구독자 수 (비공개인 경우 'Hidden')
                try {
                    if (Integer.parseInt(_subscriberCount) >= 1000000) {
                        _subscriberCount = Integer.parseInt(_subscriberCount) / 1000000 + "M";
                    }
                    if (Integer.parseInt(_subscriberCount) >= 1000) {
                        _subscriberCount = Integer.parseInt(_subscriberCount) / 1000 + "K";
                    }
                } catch (NumberFormatException e) {
                    // Hidden
                }

                Integer _videoCount = channelStatistics.get("videoCount").getAsInt(); // 동영상 수

                String _channelThumbnail = channelSnippet.get("thumbnails").getAsJsonObject()
                        .get("high").getAsJsonObject()
                        .get("url").getAsString(); // 프로필 이미지 URL

                // DTO에 데이터 설정
                channelDTO.setChannelId(channelId);

                channelDTO.setChannelTitle(_channelTitle);
                channelDTO.setChannelUrl(_channelUrl);
                channelDTO.setCreatedAt(_publishedAt);
                channelDTO.setSubscriberCount(_subscriberCount);
                channelDTO.setNumContent(_videoCount);
                channelDTO.setChannelThumbnail(_channelThumbnail);
            }

            client.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return channelDTO;
    }

    // 분석 소요 파악
    public void checkUpdate(String channelId, String apiKey) {
        // 댓글 수 변화 측정

        // 마지막 검사 단위로부터 100개 추가됐는지 측정

        // 지금은 그냥 최근 영상 1개의 본문과 최근 100개 댓글 무조건 검사함
        // 나중엔 큐에 넣어서 하겠지
        String idContent = getLatestVideoId(channelId, apiKey);
        JsonObject inputJson;

        // 채널과 콘텐츠 ID DB갱신
        Channel channel = new Channel();
        channel.setChannelId(channelId);
        System.out.println(channelId);
        channelRepository.save(channel);
        Content content = new Content();
        content.setContentId(idContent);
        content.setChannel(channel);
        contentRepository.save(content);

        // 본문 분석
        inputJson = get_data_content(apiKey, channelId, idContent);

        setKeywordData(channelId, idContent, false, inputJson);

        // 댓글 분석
        inputJson = get_data_comment(apiKey, idContent, 2, 1);
        splitJsonObject(inputJson, 2, 1);
        setKeywordData(channelId, idContent, true, inputJson);

        // return 0;
    }

    // 본문 입력 json 뽑기
    public JsonObject get_data_content(String apiKey, String channelId, String idContent) {
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
                outputJson.add("content", dataArray);

                // 출력
                // System.out.println(outputJson.toString());

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
    public JsonObject get_data_comment(String apiKey, String videoId, long numChunk, long numRemainder) {
        // YouTube API URL 설정 (댓글 목록 가져오기)
        String commentUrl = "https://www.googleapis.com/youtube/v3/commentThreads?part=snippet&videoId="
                + videoId + "&maxResults=100&key=" + apiKey;

        List<String> comments = new ArrayList<>();
        long totalFetched = 0;
        String nextPageToken = null;

        // HttpClient 생성
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            while (true) {
                // 댓글 목록 요청 (페이지네이션 처리)
                String paginatedUrl = nextPageToken == null ? commentUrl : commentUrl + "&pageToken=" + nextPageToken;
                HttpGet commentRequest = new HttpGet(paginatedUrl);
                HttpResponse commentResponse = client.execute(commentRequest);
                BufferedReader commentReader = new BufferedReader(
                        new InputStreamReader(commentResponse.getEntity().getContent(), "UTF-8"));
                StringBuilder commentJsonResponse = new StringBuilder();
                String line;

                // 응답 읽기
                while ((line = commentReader.readLine()) != null) {
                    commentJsonResponse.append(line);
                }
                commentReader.close();

                // JSON 응답 파싱
                JsonObject commentJsonObject = JsonParser.parseString(commentJsonResponse.toString()).getAsJsonObject();
                JsonArray commentItems = commentJsonObject.getAsJsonArray("items");

                // 최신 순 댓글 가져오기
                for (JsonElement item : commentItems) {
                    String commentText = item.getAsJsonObject().getAsJsonObject("snippet")
                            .getAsJsonObject("topLevelComment").getAsJsonObject("snippet")
                            .get("textDisplay").getAsString();
                    comments.add(commentText);
                    totalFetched++;

                    // 필요 개수만큼 댓글을 수집
                    if (totalFetched >= (numChunk * 100 + numRemainder)) {
                        break;
                    }
                }

                // 댓글 수가 충분하다면 종료
                if (totalFetched >= (numChunk * 100 + numRemainder)) {
                    break;
                }

                // 다음 페이지가 있다면, 다음 페이지로 이동
                nextPageToken = commentJsonObject.has("nextPageToken")
                        ? commentJsonObject.get("nextPageToken").getAsString()
                        : null;
                if (nextPageToken == null) {
                    break; // 더 이상 댓글이 없으면 종료
                }
            }

            // 결과를 JSON 형태로 반환
            JsonObject result = new JsonObject();
            JsonArray commentArray = new JsonArray();
            for (String comment : comments) {
                commentArray.add(comment);
            }
            result.add("comment", commentArray);

            // System.out.println("Number of comments: " + commentArray.size());

            // 파일 생성 및 쓰기
            String desktopPath = System.getProperty("user.home") + "/Desktop/";
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(desktopPath + "fileName"))) {
                writer.write(result.toString());
                System.out.println("JSON data saved to file: " + desktopPath + "fileName");
            } catch (IOException e) {
                e.printStackTrace();
            }

            // 반환
            return result;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // 댓글 100개 단위의 청크 리스트로 재구성; 나머지 부분은 버림
    public static List<JsonObject> splitJsonObject(JsonObject input, long numChunk, long remainder) {
        List<JsonObject> result = new ArrayList<>();

        // "comment" 배열을 가져오기
        JsonArray commentArray = input.getAsJsonArray("comment");

        // 100개의 아이템씩 담기
        int totalChunks = (int) numChunk; // numChunk는 전체 chunk의 수 (100개씩 나눈 덩어리 수)
        for (int i = 0; i < totalChunks; i++) {
            JsonObject chunk = new JsonObject();
            JsonArray chunkArray = new JsonArray();

            // 한 chunk에 들어갈 인덱스를 계산
            int startIdx = (int) (remainder + i * 100);
            int endIdx = startIdx + 100;

            for (int j = startIdx; j < endIdx; j++) {
                chunkArray.add(commentArray.get(j));
            }
            chunk.add("comment", chunkArray);

            // 파일 생성 및 쓰기
            String desktopPath = System.getProperty("user.home") + "/Desktop/";
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(desktopPath + i + "chunk"))) {
                writer.write(chunk.toString());
                System.out.println("JSON data saved to file: " + desktopPath + i + "chunk");
            } catch (IOException e) {
                e.printStackTrace();
            }

            result.add(chunk);
        }

        return result;
    }

    // 키워드 데이터 DB에 저장
    public void setKeywordData(String channelId, String idContent, boolean isComment,
            JsonObject inputJson) {

        KeywordDTO keywordDTO = new KeywordDTO();

        // 동영상 정보와 댓글을 JSON 형태로 Flask 서버에 보낼 준비 완료
        // Flask 서버로 본문 또는 댓글 정보 전송
        JsonNode flaskResponse = sendJsonToFlaskServer(inputJson);

        // System.out.println("JSON file created at: " + jsonFile.getAbsolutePath());

        /// !!! 박근원
        /// 파라미터 추가
        /// idContent : 비디오 id
        /// isComment : 코멘트(댓글) 분석이면 true, 콘텐트(본문) 분석이면 false
        /// inputJson : 양식은 무조건 "data" 산하의 텍스트 배열

        if (!isComment) {
            // content를 db에 저장

            // 채널 확인
            Channel channel = channelRepository.findById(channelId)
                    .orElseThrow(() -> new IllegalArgumentException("Channel ID가 존재하지 않습니다."));

            // 키워드 노드 가져오기
            JsonNode keywordsNode = flaskResponse.path("keywords");

            for (JsonNode keywordNode : keywordsNode) {
                String keyword = keywordNode.get("keyword").asText();
                int found = keywordNode.get("found").asInt();

                // 같은 키워드가 이미 존재하는지 확인
                Optional<ContentKeyword> existingKeywordOpt = contentKeywordRepository
                        .findByContentKeyAndChannel(keyword, channel);

                if (existingKeywordOpt.isPresent()) {
                    // 존재하는 경우: found 값을 누적하고 업데이트
                    ContentKeyword existingKeyword = existingKeywordOpt.get();
                    existingKeyword.setFound(existingKeyword.getFound() + found);
                    contentKeywordRepository.save(existingKeyword); // 업데이트 저장
                } else {
                    // 존재하지 않는 경우: 새로운 키워드 엔티티 생성 및 저장
                    ContentKeyword contentKeyword = new ContentKeyword();
                    contentKeyword.setChannel(channel);
                    contentKeyword.setContentKey(keyword);
                    contentKeyword.setFound(found);
                    contentKeywordRepository.save(contentKeyword);

                }

            }

        } else {
            // comment를 db에 저장

            // 채널 확인
            Channel channel = channelRepository.findById(channelId)
                    .orElseThrow(() -> new IllegalArgumentException("Channel ID가 존재하지 않습니다."));

            // 콘텐츠도 확인
            Content content = contentRepository.findById(idContent)
                    .orElseThrow(() -> new IllegalArgumentException("Content ID가 존재하지 않습니다."));

            // 키워드 노드 가져오기
            JsonNode keywordsNode = flaskResponse.path("keywords");

            for (JsonNode keywordNode : keywordsNode) {
                String keyword = keywordNode.get("keyword").asText();
                int found = keywordNode.get("found").asInt();

                // 같은 키워드가 이미 존재하는지 확인
                Optional<CommentKeyword> existingKeywordOpt = commentKeywordRepository
                        .findByCommentKeyAndChannelAndContent(keyword, channel, content);

                if (existingKeywordOpt.isPresent()) {
                    // 존재하는 경우: found 값을 누적하고 업데이트
                    CommentKeyword existingKeyword = existingKeywordOpt.get();
                    existingKeyword.setFound(existingKeyword.getFound() + found);
                    commentKeywordRepository.save(existingKeyword); // 업데이트 저장
                } else {
                    // 존재하지 않는 경우: 새로운 키워드 엔티티 생성 및 저장
                    CommentKeyword commentKeyword = new CommentKeyword();
                    commentKeyword.setChannel(channel);
                    commentKeyword.setContent(content);
                    commentKeyword.setCommentKey(keyword);
                    commentKeyword.setFound(found);
                    commentKeywordRepository.save(commentKeyword);

                }

            }

        }

    }

    // Flask 서버로 동영상 데이터를 전송하고 받는 메서드
    private JsonNode sendJsonToFlaskServer(JsonObject outputJson) {

        String flaskUrl = "http://localhost:5000/respondK"; // 키워드서버
        // System.out.println("Sending JSON to Flask: " + outputJson.toString()); // 전송할
        // JSON 출력

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
            // System.out.println("Response from Flask: " + responseBody);

            if (statusCode != 200) {
                String errorMessage = "Flask 서버와의 통신 중 오류 발생: " + statusCode + ". 응답 본문: " + responseBody;
                System.out.println(errorMessage);
                throw new IOException(errorMessage);
            } else {
                System.out.println("Flask 서버와의 통신 성공: " + statusCode);
            }

            // 응답 본문을 읽어와서 JSON으로 파싱 -> Jackson 라이브러리를 활용 코드로 변경
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readTree(responseBody);

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;

    }

    // 워드클라우드 그래프 - 모든 키워드 상위 100개
    public WordCloudDTO getWordCloudData(String channelId) {

        List<Object[]> results = contentKeywordRepository.findTop100ByChannelIdOrderByFoundDesc(channelId);

        // 키워드 리스트와 found 리스트 생성
        List<String> keyList = results.stream()
                .map(result -> (String) result[0])
                .collect(Collectors.toList());
        List<Long> foundList = results.stream()
                .map(result -> (Long) result[1])
                .collect(Collectors.toList());

        // Console 출력
        // System.out.println("Key List: " + keyList);
        // System.out.println("Found List: " + foundList);

        // KeywordDTO에 저장하여 반환
        return new WordCloudDTO(keyList, foundList);

    }

    // 주제 키워드 랭킹 - 본문 키워드 상위 10개
    public KeywordDTO getRankingData(String channelId) {
        List<ContentKeyword> topKeywords = contentKeywordRepository.findTop10ByChannelIdOrderByFoundDesc(channelId);

        List<String> keyList = new ArrayList<>();
        List<Integer> foundList = new ArrayList<>();

        for (ContentKeyword keyword : topKeywords) {
            keyList.add(keyword.getContentKey());
            // System.out.println("본문 키워드: " + keyword.getContentKey());
            foundList.add(keyword.getFound());
            // System.out.println("댓글 키워드: " + keyword.getFound());
        }

        return new KeywordDTO(keyList, foundList);
    }

    // 파이 차트 - 댓글 키워드 (동영상 당 8개)
    public KeywordDTO getPieData(String channelId) {
        List<Object[]> topKeywords = commentKeywordRepository
                .findTop8ByChannelIdAndCommentIdOrderByFoundDesc(channelId);

        List<String> keyList = new ArrayList<>();
        List<Integer> foundList = new ArrayList<>();

        for (Object[] keyword : topKeywords) {
            keyList.add((String) keyword[0]);
            // System.out.println("댓글 키워드: " + keyword[0]);
            foundList.add((Integer) keyword[1]);
            // System.out.println("댓글 점수: " + keyword[1]);
        }

        return new KeywordDTO(keyList, foundList);
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
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class YouTubeService {

    private final CommentKeywordRepository commentKeywordRepository;
    private final ContentKeywordRepository contentKeywordRepository;
    private final ChannelRepository channelRepository;
    private final ContentRepository contentRepository;

    private final FlaskQueueService taskQueueService;

    // 채널 ID 조회
    public boolean isChannelIdExists(String channelId) {
        return channelRepository.existsById(channelId);
    }

    // 처음 데이터를 받아올 때 채널/콘텐츠/콘텐츠 키워드 데이터를 저장
    public void saveChannelData(ChannelDTO channelDTO, String apiKey) {

        String channelId = channelDTO.getChannelId();

        // 채널 DB 신규 갱신
        Channel channel = new Channel();
        channel.setChannelId(channelDTO.getChannelId());
        channel.setChannelTitle(channelDTO.getChannelTitle());
        channel.setContentNum(channelDTO.getContentNum());
        channelRepository.save(channel);

        try {
            // 채널의 모든 비디오 목록 가져오기 (일단 2개)
            String videoListUrl = "https://www.googleapis.com/youtube/v3/search?key=" + apiKey + "&channelId="
                    + channelId + "&part=snippet&type=video&maxResults=2"; // maxResults를 2로 설정

            List<JsonObject> allVideos = new ArrayList<>();
            String nextPageToken = null;

            while (true) {
                // 페이지네이션 처리: 다음 페이지가 있다면, nextPageToken을 URL에 추가
                String paginatedUrl = nextPageToken == null ? videoListUrl
                        : videoListUrl + "&pageToken=" + nextPageToken;

                // HTTP 요청 및 응답 처리
                URL url = new URL(paginatedUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");

                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                // JSON 응답 파싱
                JsonObject responseJson = JsonParser.parseString(response.toString()).getAsJsonObject();
                JsonArray items = responseJson.getAsJsonArray("items");

                // 동영상 정보 수집
                for (JsonElement item : items) {
                    allVideos.add(item.getAsJsonObject());
                }

                // 다음 페이지가 있다면 nextPageToken을 가져오고, 없으면 종료
                nextPageToken = responseJson.has("nextPageToken")
                        ? responseJson.get("nextPageToken").getAsString()
                        : null;

                // 각 컨텐츠 ID를 DB에 저장
                for (int i = 0; i < items.size(); i++) {
                    JsonObject video = items.get(i).getAsJsonObject();
                    String videoId = video.getAsJsonObject("id").get("videoId").getAsString();

                    // 콘텐츠 정보 DB에 등록
                    Content content = new Content();
                    if (!contentRepository.existsById(videoId)) {
                        content.setContentId(videoId);
                        // content.setCommentNum(commentCount);
                        // 테스트 값
                        content.setCommentNum(0);
                        content.setChannel(channel);
                        contentRepository.save(content);
                    }
                }

                if (nextPageToken == null) {
                    break; // 더 이상 페이지가 없으면 종료
                } else {
                    break; // 일단 한페이지만 받아보자 (5개)
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

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
                channelDTO.setContentNum(_videoCount);
                channelDTO.setChannelThumbnail(_channelThumbnail);

                // 해당 DTO에 채널 순위 추가
                channelDTO.setRank(getChannelRank(channelId));

                // 업데이트된 게시글 수도 보내기
                channelDTO.setUpdateAnchorNum(getChannelUpdateAnchorNum(channelId));
            }

            client.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return channelDTO;
    }

    // "열람 가능한" 댓글 페이지 수
    public int getCommentFullPageCount(String apiKey, String idContent) {
        String commentUrl = "https://www.googleapis.com/youtube/v3/commentThreads?part=snippet&videoId="
                + idContent + "&maxResults=100&key=" + apiKey;

        int pageCount = 0;
        String nextPageToken = null;

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            while (true) {
                // 페이지네이션 처리
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

                // 페이지 카운트 증가
                pageCount++;

                // 다음 페이지로 이동
                nextPageToken = commentJsonObject.has("nextPageToken")
                        ? commentJsonObject.get("nextPageToken").getAsString()
                        : null;

                // 다음 페이지가 없으면 종료
                if (nextPageToken == null) {
                    JsonArray commentItems = commentJsonObject.getAsJsonArray("items");
                    Integer lastPageCommentCount = commentItems.size();

                    if (lastPageCommentCount < 100) {
                        pageCount--;
                    }
                    break;
                }
            }

            System.out.println("Total pages of comments: " + pageCount);
            return pageCount;

        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    // 기존에 데이터가 있는 경우 분석 소요 파악
    public void checkUpdate(String channelId, String apiKey) {

        // 조회수 증가
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new RuntimeException("Channel not found with id: " + channelId));
        Integer anchorNum = channel.getAnchorNum();
        channel.setAnchorNum(anchorNum + 1);
        channelRepository.save(channel);

        int numSubject = Math.min(channel.getContentNum(), 10);

        // 첫 검색 시 분석/작업 생성
        if (channel.getAnchorNum() == 1) {
            // 비디오 ID 및 댓글 수 리스트 만들기
            List<String> ls_new_idContent = new ArrayList<>();

            try {
                // ----------------------------------- 최신순 영상 10개 로드
                String videoListUrlDate = "https://www.googleapis.com/youtube/v3/search?key=" + apiKey + "&channelId="
                        + channelId + "&part=snippet&type=video&maxResults=10&order=date"; // 최신순으로 정렬

                // HTTP 요청 및 응답 처리 (최신순)
                URL urlDate = new URL(videoListUrlDate);
                HttpURLConnection connectionDate = (HttpURLConnection) urlDate.openConnection();
                connectionDate.setRequestMethod("GET");

                BufferedReader readerDate = new BufferedReader(new InputStreamReader(connectionDate.getInputStream()));
                StringBuilder responseDate = new StringBuilder();
                String line;
                while ((line = readerDate.readLine()) != null) {
                    responseDate.append(line);
                }
                readerDate.close();

                // JSON 응답 파싱 (최신순)
                JsonObject responseJsonDate = JsonParser.parseString(responseDate.toString()).getAsJsonObject();
                JsonArray itemsDate = responseJsonDate.getAsJsonArray("items");

                // ----------------------------------- 관련성 영상 10개 로드
                String videoListUrlRelevance = "https://www.googleapis.com/youtube/v3/search?key=" + apiKey
                        + "&channelId="
                        + channelId + "&part=snippet&type=video&maxResults=50&order=relevance"; // 관련성 기준으로 정렬

                // HTTP 요청 및 응답 처리 (관련성)
                URL urlRelevance = new URL(videoListUrlRelevance);
                HttpURLConnection connectionRelevance = (HttpURLConnection) urlRelevance.openConnection();
                connectionRelevance.setRequestMethod("GET");

                BufferedReader readerRelevance = new BufferedReader(
                        new InputStreamReader(connectionRelevance.getInputStream()));
                StringBuilder responseRelevance = new StringBuilder();
                while ((line = readerRelevance.readLine()) != null) {
                    responseRelevance.append(line);
                }
                readerRelevance.close();

                // JSON 응답 파싱 (관련성)
                JsonObject responseJsonRelevance = JsonParser.parseString(responseRelevance.toString())
                        .getAsJsonObject();
                JsonArray itemsRelevance = responseJsonRelevance.getAsJsonArray("items");

                // ----------------------------------- numSubject만큼 리스트 채우기

                JsonObject videoJson;
                String videoId;

                int cntDate = 0;
                int cntRele = 0;
                while (true) {
                    // 다 채우면 탈출(1)
                    if (ls_new_idContent.size() == numSubject) {
                        break;
                    }

                    // 관련성 id 추가
                    videoJson = itemsRelevance.get(cntRele++).getAsJsonObject();
                    videoId = videoJson.getAsJsonObject("id").get("videoId").getAsString();

                    if (ls_new_idContent.contains(videoId) == false) {
                        ls_new_idContent.add(videoId);
                    }

                    // 다 채우면 탈출(2)
                    if (ls_new_idContent.size() == numSubject) {
                        break;
                    }

                    // 최신순 id 추가
                    videoJson = itemsDate.get(cntDate++).getAsJsonObject();
                    videoId = videoJson.getAsJsonObject("id").get("videoId").getAsString();

                    if (ls_new_idContent.contains(videoId) == false) {
                        ls_new_idContent.add(videoId);
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

            // 작업 생성
            JsonObject allComments;
            List<JsonObject> commentChunk;

            // 작업 큐에 삽입
            for (int i = 0; i < numSubject; i++) {
                // 분석할 콘텐츠의 ID
                String theId = ls_new_idContent.get(i);
                Integer numChunks = Math.min(getCommentFullPageCount(apiKey, theId), 10); // 최대 10페이지

                // 전부 읽어서
                allComments = get_data_comment(apiKey, theId, numChunks);

                // 최대 100개 단위로 끊음
                commentChunk = splitJsonObject(allComments, numChunks);

                // 본문 분석 작업 요청
                addTaskToQueue(channelId, theId, false, get_data_content(apiKey, channelId, theId), false);
                System.out.println("본문" + (i + 1) + "분석 요청");

                // 댓글 분석 작업 요청
                for (int j = 0; j < numChunks; j++) {
                    boolean isEndling = (j == numChunks - 1 ? true : false);
                    addTaskToQueue(channelId, theId, true, commentChunk.get(j), isEndling);
                    System.out.println("댓글" + (i + 1) + "-" + (j + 1) + "분석 요청");
                }
            }
        }
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
    public JsonObject get_data_comment(String apiKey, String videoId, long numChunk) {
        // YouTube API URL 설정 (댓글 목록 가져오기)
        String commentUrl = "https://www.googleapis.com/youtube/v3/commentThreads?part=snippet&videoId="
                + videoId + "&maxResults=100&key=" + apiKey;

        List<String> comments = new ArrayList<>();
        String nextPageToken = null;

        int cntChunk = 0;

        // HttpClient 생성
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            while (true) {
                // n번째 청크
                cntChunk += 1;

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
                }

                // 모든 청크를 읽었다면 종료
                if (cntChunk >= numChunk) {
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

            // 반환
            return result;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // 댓글 100개 단위의 청크 리스트로 재구성; 나머지 부분은 버림
    public static List<JsonObject> splitJsonObject(JsonObject input, Integer numChunk) {
        List<JsonObject> result = new ArrayList<>();

        // "comment" 배열을 가져오기
        JsonArray commentArray = input.getAsJsonArray("comment");

        // 100개의 아이템씩 담기
        int totalChunks = (int) numChunk; // numChunk는 전체 chunk의 수 (100개씩 나눈 덩어리 수)
        for (int i = 0; i < totalChunks; i++) {
            JsonObject chunk = new JsonObject();
            JsonArray chunkArray = new JsonArray();

            // 한 chunk에 들어갈 인덱스를 계산
            int startIdx = i * 100;
            int endIdx = startIdx + 99;

            for (int j = startIdx; j < Math.min(endIdx, commentArray.size()); j++) {
                chunkArray.add(commentArray.get(j));
            }
            chunk.add("comment", chunkArray);

            result.add(chunk);
        }

        return result;
    }

    // 키워드 데이터 DB에 저장
    public void setKeywordData(String channelId, String idContent, boolean isComment,
            JsonObject inputJson, boolean isEndling) {

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

            JsonNode sentimentNode = flaskResponse.path("compound_score");
            Double double_sent = sentimentNode.asDouble(-1.1);

            System.out.println("감정점수 뽑힘" + double_sent);

            Content contentUpdate = contentRepository.findByChannelAndContentId(channel, idContent)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Channel not found with ID: " + channelId + " " + idContent));

            Double old = contentUpdate.getSentiment();
            contentUpdate.setSentiment(old + double_sent);
            contentRepository.save(contentUpdate);

        }

        if (isEndling) {
            Channel channel = channelRepository.findById(channelId)
                    .orElseThrow(() -> new RuntimeException("Channel not found with id: " + channelId));
            // 아무 변수만들어서 getUpdateAnchorNum(); 해서 기존치 가져옴
            Integer updateAnchorNum = channel.getUpdateAnchorNum();
            // +1 해줌
            channel.setUpdateAnchorNum(updateAnchorNum + 1);
            // 저장
            channelRepository.save(channel);
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

        List<Object[]> results = contentKeywordRepository.findTop50ByChannelIdOrderByFoundDesc(channelId);

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
    public List<PieDTO> getPieData(String channelId) {
        // Step 1: Get the latest 5 contentIds for the given channelId
        List<String> contentIds = contentRepository.findLatestContentIdsByChannelId(channelId);

        // Maps to store keywords and their found counts
        Map<String, List<String>> keyMap = new HashMap<>();
        Map<String, List<Integer>> foundMap = new HashMap<>();
        // Step 2: For each contentId, get the top 8 keywords and their found counts
        for (String contentId : contentIds) {
            List<Object[]> keywordsData = commentKeywordRepository.findTopKeywordsByContentId(contentId);

            List<String> keys = new ArrayList<>();
            List<Integer> founds = new ArrayList<>();

            for (Object[] row : keywordsData) {
                keys.add((String) row[0]);
                founds.add((Integer) row[1]);
            }

            keyMap.put(contentId, keys);
            foundMap.put(contentId, founds);
        }

        // Step 3: Create PieDTO objects and add them to the result list
        List<PieDTO> result = new ArrayList<>();
        for (String contentId : contentIds) {
            Double sentiment = contentRepository.findSentimentByContentId(contentId, channelId);
            PieDTO pieDTO = new PieDTO();
            pieDTO.setContentId(contentId);
            pieDTO.setKeyList(keyMap.get(contentId));
            pieDTO.setFoundList(foundMap.get(contentId));
            pieDTO.setSentiment(sentiment);
            result.add(pieDTO);
        }

        return result;

    }

    public Object getGrowthData(String channelId) {

        List<Integer> growthContent = new ArrayList<>();
        List<Integer> growthComment = new ArrayList<>();

        return null;
    }

    // 작업 큐에 추가
    void addTaskToQueue(String channelId, String idContent, boolean isComment, JsonObject inputJson,
            boolean isEndling) {
        Runnable task = () -> setKeywordData(channelId, idContent, isComment, inputJson, isEndling);
        taskQueueService.addTask(task);
        System.err.println("!!!작업 추가됨");
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

    public ChannelDTO getChannelDBData(ChannelDTO channelDTO) {

        String channelId = channelDTO.getChannelId();
        Channel channel = channelRepository.findByChannelId(channelId)
                .orElseThrow(() -> new IllegalArgumentException("Channel not found with ID: " + channelId));

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDateTime fullCreatedAt = channel.getCreatedAt();
        LocalDateTime fullUpdateAt = channel.getCreatedAt();

        String createAtDB = getFormattedCreatedAt(fullCreatedAt);
        String updateAt = getFormattedCreatedAt(fullUpdateAt);

        channelDTO.setCreatedAtDB(createAtDB);
        channelDTO.setUpdatedAt(updateAt);
        channelDTO.setAnchorNum(channel.getAnchorNum());

        return channelDTO;
    }

    public String getFormattedCreatedAt(LocalDateTime createdAt) {
        if (createdAt == null) {
            return null; // 예외 처리 또는 기본값 반환
        }
        // 'YYYY-MM-DD' 포맷으로 변환
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        return createdAt.format(formatter);
    }

    // 인기
    public List<SearchTrendsDTO> getPopularKeywords() {
        Pageable pageable = PageRequest.of(0, 10); // 상위 10개
        List<Channel> popularChannels = channelRepository.findByOrderByAnchorNumDesc(pageable);
        return popularChannels.stream()
                .map(c -> new SearchTrendsDTO(c.getChannelTitle(), c.getAnchorNum(), null))
                .collect(Collectors.toList());
    }

    // 최근
    public List<SearchTrendsDTO> getRecentKeywords() {
        Pageable pageable = PageRequest.of(0, 10); // 상위 10개
        List<Channel> recentChannels = channelRepository.findByOrderByUpdatedAtDesc(pageable);
        return recentChannels.stream()
                .map(c -> new SearchTrendsDTO(c.getChannelTitle(), 0, c.getUpdatedAt()))
                .collect(Collectors.toList());
    }

    public int getChannelRank(String channelId) {
        // 모든 데이터를 anchorNum 기준 내림차순으로 가져옴
        List<Channel> channels = channelRepository.findAllByOrderByAnchorNumDesc();

        // 순위 계산
        for (int i = 0; i < channels.size(); i++) {
            if (channels.get(i).getChannelId().equals(channelId)) {
                return i + 1; // 0부터 시작하므로 1을 더해 순위를 반환
            }
        }

        // 데이터가 없으면 -1 반환
        return -1;
    }

    public Integer getChannelUpdateAnchorNum(String channelId) {
        return channelRepository.findUpdateAnchorNumByChannelId(channelId);
    }
}

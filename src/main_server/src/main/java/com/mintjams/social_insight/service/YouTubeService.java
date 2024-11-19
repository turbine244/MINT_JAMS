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
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

                JsonObject inputJson;
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

                        // 본문 분석 작업 요청
                        inputJson = get_data_content(apiKey, channelId, videoId);
                        addTaskToQueue(channelId, videoId, false, inputJson);

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

            System.out.println("gogogogogo");

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

                System.out.println("gogogogogo2");

                // DTO에 데이터 설정
                channelDTO.setChannelId(channelId);

                channelDTO.setChannelTitle(_channelTitle);
                channelDTO.setChannelUrl(_channelUrl);
                channelDTO.setCreatedAt(_publishedAt);
                channelDTO.setSubscriberCount(_subscriberCount);
                channelDTO.setContentNum(_videoCount);
                channelDTO.setChannelThumbnail(_channelThumbnail);

                System.out.println("gogogogogo3");
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

        // 앵커 증가
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new RuntimeException("Channel not found with id: " + channelId));
        Integer anchorNum = channel.getAnchorNum();
        channel.setAnchorNum(anchorNum + 1);
        channelRepository.save(channel);

        // 분석 지표; 비디오 ID 및 댓글 수 리스트 만들기
        List<String> ls_new_idContent = new ArrayList<>();
        List<Integer> ls_new_numFullCommentPage = new ArrayList<>();

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

                // 각 비디오에 대해 댓글 페이지 수 가져오기
                for (int i = 0; i < items.size(); i++) {
                    JsonObject video = items.get(i).getAsJsonObject();
                    String videoId = video.getAsJsonObject("id").get("videoId").getAsString();

                    Integer commentCount = getCommentFullPageCount(apiKey, videoId);
                    // remainder

                    // videoId와 commentCount를 List에 저장
                    ls_new_idContent.add(videoId);
                    ls_new_numFullCommentPage.add(commentCount);

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

        // DB에서 관리 중인 정보 불러오기
        // DB에 저장된; 해당 채널의 콘텐츠 id들
        List<String> ls_db_idContent = contentRepository.findContentIdsByChannelId(channelId);
        // DB에 저장된; 콘텐츠들의 댓글 갯수 (위 리스트와 인덱스 같음)
        List<Integer> ls_db_numFullCommentPage = contentRepository.findCommentNumsByChannelId(channelId);

        System.out.println("ls_db_idContent출력");
        // 또는 for-each 루프를 사용하여 각 항목을 출력
        for (String s : ls_db_idContent) {
            System.out.println(s);
        }

        System.out.println("ls_db_numFullCommentPage출력");
        // 또는 for-each 루프를 사용하여 각 항목을 출력
        for (Integer num : ls_db_numFullCommentPage) {
            System.out.println(num);
        }

        JsonObject inputJson;
        List<JsonObject> commentChunk;

        // 분석 소요 파악; 작업 큐에 삽입
        for (int i = 0; i < ls_new_idContent.size(); i++) {
            // 분석할 콘텐츠의 ID
            String theId = ls_new_idContent.get(i);

            // 만약 theId가 기존 DB에 없으면 DB에 등록하고 본문 분석 작업 요청
            if (!contentRepository.existsById(theId)) {
                Content content = new Content();
                content.setContentId(theId);
                // content.setCommentNum(commentCount);
                // 테스트 값
                content.setCommentNum(0);
                content.setChannel(channel);
                contentRepository.save(content);

                // 본문 분석 작업 요청
                inputJson = get_data_content(apiKey, channelId, theId);
                addTaskToQueue(channelId, theId, false, inputJson);
            }

            // 댓글 분석 시작할 지점 (100개 단위)
            Integer offset = 0;

            if (ls_db_numFullCommentPage.get(ls_db_idContent.indexOf(theId)) == null) {
                offset = 0;
            } else {
                offset = ls_db_numFullCommentPage.get(ls_db_idContent.indexOf(theId));
            }

            // 추가된 댓글 묶음 수
            Integer newHundreds = ls_new_numFullCommentPage.get(i) - offset;
            System.out.println("new" + ls_new_numFullCommentPage.get(i));
            System.out.println("offset " + offset);
            offset = ls_new_numFullCommentPage.get(i);

            // 100 나머지
            Integer remainder = ls_new_numFullCommentPage.get(i) % 100;

            // 일단 필요한 만큼 읽어서
            inputJson = get_data_comment(apiKey, theId, newHundreds, remainder);

            // 나머지는 버리고 100개 단위로 끊음
            System.out.println("앞");
            commentChunk = splitJsonObject(inputJson, newHundreds, remainder);
            System.out.println("뒤");

            // !!!!!!!!임시 수정사항!!!!!!!!! - 조수정
            // newHundreds가 너무 많으면 오래 걸려서, 10 이상이면 10으로 고정해둠...
            if (newHundreds > 10) {
                newHundreds = 10;
            }

            // 댓글 분석 작업 요청 (100개 단위)
            for (int j = 0; j < newHundreds; j++) {
                System.out.println("중");
                addTaskToQueue(channelId, theId, true, commentChunk.get(j));
                System.out.println(i + " " + j);
            }

            // 업데이트
            Content content = contentRepository.findById(ls_new_idContent.get(i))
                    .orElseThrow(() -> new RuntimeException("Content not found with id"));
            content.setCommentNum(offset);
            contentRepository.save(content);

        }

        // DB 갱신; 추가된 콘텐츠들
        // 1. ls_new_idContent에 새로 추가된 애들만 남김
        ls_new_idContent.removeIf(item -> ls_db_idContent.contains(item));
        // 2. 뭔가 하기
        for (int i = 0; i < ls_new_idContent.size(); i++) {

        }

        // DB 갱신; 콘텐츠+코멘트 - 삭제된 콘텐츠 정보 + 부속 코멘트 키워드 처리
        // 1. ls_db_idContent에 삭제된 애들만 남김
        ls_db_idContent.removeIf(item -> ls_new_idContent.contains(item));
        // 2. DB에서 모든 관련 내용을 제거
        // 부탁해요

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
    public static List<JsonObject> splitJsonObject(JsonObject input, Integer numChunk, Integer remainder) {
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

            JsonNode sentimentNode = flaskResponse.path("compound_score");
            Double double_sent = sentimentNode.asDouble(-1.1);

            System.out.println("감정점수 뽑힘" + double_sent);

            Channel channelUpdate = channelRepository.findByChannelId(channelId)
                    .orElseThrow(() -> new IllegalArgumentException("Channel not found with ID: " + channelId));
            channelUpdate.setSentiment(double_sent);
            channelRepository.save(channelUpdate);


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

        return new KeywordDTO(keyList, foundList, 0.0);
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

        // DB에서 감정 가져오기
        Double double_sent = channelRepository.findSentimentByChannelId(channelId);

        return new KeywordDTO(keyList, foundList, double_sent);
    }

    public Object getGrowthData(String channelId) {

        List<Integer> growthContent = new ArrayList<>();
        List<Integer> growthComment = new ArrayList<>();

        return null;
    }

    // 작업 큐에 추가
    void addTaskToQueue(String channelId, String idContent, boolean isComment, JsonObject inputJson) {
        Runnable task = () -> setKeywordData(channelId, idContent, isComment, inputJson);
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

}
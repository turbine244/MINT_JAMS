package com.example;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;

public class Main {

    public static void main(String[] args) {
        String videoId = "qWbHSOplcvY"; // 동영상 ID를 입력하세요
        String apiKey = "AIzaSyBOnvoVM2O60KA30ReM-No_OzcvQjjk68w"; // Google Cloud에서 받은 API 키

        // 동영상 세부 정보 URL
        String videoInfoUrl = "https://www.googleapis.com/youtube/v3/videos?part=snippet,statistics&id=" + videoId
                + "&key=" + apiKey;

        // 댓글 정보 URL
        String commentUrl = "https://www.googleapis.com/youtube/v3/commentThreads?part=snippet&videoId=" + videoId
                + "&maxResults=100&key=" + apiKey;

        try {
            CloseableHttpClient client = HttpClients.createDefault();

            // 동영상 세부 정보 가져오기
            HttpGet videoRequest = new HttpGet(videoInfoUrl);
            HttpResponse videoResponse = client.execute(videoRequest);
            BufferedReader videoReader = new BufferedReader(
                    new InputStreamReader(videoResponse.getEntity().getContent(), "UTF-8"));
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

            // 동영상 제목 및 본문 내용 저장
            JsonObject outputJson = new JsonObject();
            if (videoItems.size() > 0) {
                JsonObject videoSnippet = videoItems.get(0).getAsJsonObject().getAsJsonObject("snippet");
                JsonObject videoStatistics = videoItems.get(0).getAsJsonObject().getAsJsonObject("statistics");

                String title = videoSnippet.get("title").getAsString();
                String description = videoSnippet.get("description").getAsString();
                String publishedAt = videoSnippet.get("publishedAt").getAsString(); // 업로드 날짜
                String viewCount = videoStatistics.get("viewCount").getAsString(); // 조회수
                String likeCount = videoStatistics.get("likeCount").getAsString(); // 좋아요 수

                outputJson.addProperty("title", title);
                outputJson.addProperty("description", description);
                outputJson.addProperty("publishedAt", publishedAt); // 업로드 날짜 추가
                outputJson.addProperty("viewCount", viewCount); // 조회수 추가
                outputJson.addProperty("likeCount", likeCount); // 좋아요 수 추가
            } else {
                System.out.println("No video found.");
                return;
            }

            // 댓글 정보 가져오기
            JsonArray commentsArray = new JsonArray();
            String nextPageToken = null;

            do {
                HttpGet commentRequest = new HttpGet(
                        commentUrl + (nextPageToken != null ? "&pageToken=" + nextPageToken : ""));
                HttpResponse commentResponse = client.execute(commentRequest);
                BufferedReader commentReader = new BufferedReader(
                        new InputStreamReader(commentResponse.getEntity().getContent(), "UTF-8"));
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
                        JsonObject snippet = item.getAsJsonObject().getAsJsonObject("snippet")
                                .getAsJsonObject("topLevelComment").getAsJsonObject("snippet");
                        String comment = snippet.get("textDisplay").getAsString();
                        commentsArray.add(comment);
                    }

                    // 다음 페이지 토큰 설정
                    nextPageToken = commentJsonObject.has("nextPageToken")
                            ? commentJsonObject.get("nextPageToken").getAsString()
                            : null;
                } else {
                    nextPageToken = null;
                }
            } while (nextPageToken != null);

            outputJson.add("comments", commentsArray);

            // 바탕 화면에 JSON 파일로 저장 (UTF-8 인코딩)
            File jsonFile = new File("D:/javashit/demo/youtube_comments.json");
            try (FileOutputStream fos = new FileOutputStream(jsonFile);
                    Writer writer = new OutputStreamWriter(fos, "UTF-8")) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create(); // pretty printing 활성화
                gson.toJson(outputJson, writer);
            }

            System.out.println("JSON file created at: " + jsonFile.getAbsolutePath());

            client.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

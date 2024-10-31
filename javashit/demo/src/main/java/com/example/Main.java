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

import java.io.*;

public class Main {

    public static void main(String[] args) {
        String channelName = "채널 이름"; // 조회할 채널 이름을 입력하세요
        String apiKey = "YOUR_API_KEY"; // Google Cloud에서 받은 API 키

        // 채널 ID 가져오기 URL
        String searchChannelUrl = "https://www.googleapis.com/youtube/v3/search?part=snippet&type=channel&q=" 
                + channelName + "&key=" + apiKey;

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
                return;
            }
            String channelId = channelItems.get(0).getAsJsonObject().getAsJsonObject("id").get("channelId").getAsString();

            // 채널의 최신 동영상 가져오기
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
                return;
            }
            String videoId = videoItems.get(0).getAsJsonObject().getAsJsonObject("id").get("videoId").getAsString();

            // 동영상 세부 정보 가져오기
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

                outputJson.addProperty("title", title);
                outputJson.addProperty("description", description);
                outputJson.addProperty("publishedAt", publishedAt);
                outputJson.addProperty("viewCount", viewCount);
                outputJson.addProperty("likeCount", likeCount);
            }

            // JSON 파일로 저장
            File jsonFile = new File("D:/javashit/demo/channel_latest_video.json");
            try (FileOutputStream fos = new FileOutputStream(jsonFile);
                 Writer writer = new OutputStreamWriter(fos, "UTF-8")) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                gson.toJson(outputJson, writer);
            }

            System.out.println("JSON file created at: " + jsonFile.getAbsolutePath());

            client.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

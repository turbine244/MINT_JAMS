public void getCommentKeywordData(String searchChannelUrl, String apiKey) {
    try (CloseableHttpClient client = HttpClients.createDefault()) {
        // 1. 채널 ID 가져오기
        HttpGet channelRequest = new HttpGet(searchChannelUrl + "&key=" + apiKey);
        HttpResponse channelResponse = client.execute(channelRequest);
        BufferedReader channelReader = new BufferedReader(
                new InputStreamReader(channelResponse.getEntity().getContent(), "UTF-8"));
        StringBuilder channelJsonResponse = new StringBuilder();
        String line;
        while ((line = channelReader.readLine()) != null) {
            channelJsonResponse.append(line);
        }
        channelReader.close();

        JsonElement channelElement = JsonParser.parseString(channelJsonResponse.toString());
        JsonObject channelObject = channelElement.getAsJsonObject();
        JsonArray channelItems = channelObject.getAsJsonArray("items");

        if (channelItems.size() == 0) {
            System.out.println("No channel found.");
            return;
        }

        String channelId = channelItems.get(0).getAsJsonObject().getAsJsonObject("id").get("channelId")
                .getAsString();

        // 2. 최신 동영상 ID 가져오기
        String videoSearchUrl = "https://www.googleapis.com/youtube/v3/search?part=snippet&channelId=" + channelId
                + "&order=date&type=video&maxResults=1&key=" + apiKey;
        HttpGet videoRequest = new HttpGet(videoSearchUrl);
        HttpResponse videoResponse = client.execute(videoRequest);
        BufferedReader videoReader = new BufferedReader(
                new InputStreamReader(videoResponse.getEntity().getContent(), "UTF-8"));
        StringBuilder videoJsonResponse = new StringBuilder();
        while ((line = videoReader.readLine()) != null) {
            videoJsonResponse.append(line);
        }
        videoReader.close();

        JsonElement videoElement = JsonParser.parseString(videoJsonResponse.toString());
        JsonObject videoObject = videoElement.getAsJsonObject();
        JsonArray videoItems = videoObject.getAsJsonArray("items");

        if (videoItems.size() == 0) {
            System.out.println("No videos found for the channel.");
            return;
        }

        String videoId = videoItems.get(0).getAsJsonObject().getAsJsonObject("id").get("videoId").getAsString();

        // 3. 댓글 가져오기
        String commentUrl = "https://www.googleapis.com/youtube/v3/commentThreads?part=snippet&videoId=" + videoId
                + "&order=time&maxResults=100&key=" + apiKey;

        HttpGet commentRequest = new HttpGet(commentUrl);
        HttpResponse commentResponse = client.execute(commentRequest);
        BufferedReader commentReader = new BufferedReader(
                new InputStreamReader(commentResponse.getEntity().getContent(), "UTF-8"));
        StringBuilder commentJsonResponse = new StringBuilder();
        while ((line = commentReader.readLine()) != null) {
            commentJsonResponse.append(line);
        }
        commentReader.close();

        JsonElement commentElement = JsonParser.parseString(commentJsonResponse.toString());
        JsonObject commentObject = commentElement.getAsJsonObject();
        JsonArray comments = commentObject.getAsJsonArray("items");

        // 4. JSON 파일로 저장 (간소화된 구조)
        try (FileWriter fileWriter = new FileWriter("comments.json")) {
            JsonArray commentsArray = new JsonArray();

            for (JsonElement item : comments) {
                String textOriginal = item.getAsJsonObject().getAsJsonObject("snippet")
                        .getAsJsonObject("topLevelComment").getAsJsonObject("snippet").get("textOriginal").getAsString();
                commentsArray.add(textOriginal); // textOriginal 값만 추가
            }

            // 최종 결과를 포함하는 JSON 객체
            JsonObject output = new JsonObject();
            output.add("comments", commentsArray);
            
            // JSON을 파일에 작성
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(output, fileWriter);
            System.out.println("Comments saved to comments.json");
        }

    } catch (Exception e) {
        e.printStackTrace();
    }
}

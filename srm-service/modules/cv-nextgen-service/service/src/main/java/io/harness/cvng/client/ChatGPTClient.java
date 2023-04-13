/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.Data;
import lombok.ToString;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ChatGPTClient {
  private static final String BASE_URL = "https://api.openai.com/v1";

  public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

  private final OkHttpClient client;
  private final Gson gson;

  public ChatGPTClient() {
    this.client = new OkHttpClient();
    this.gson = new GsonBuilder().create();
  }

  public String chatGPTResponse(String apiToken, String apiPath, String logs) throws IOException {
    String url = String.format("%s/%s", BASE_URL, apiPath);
    RequestBody body = RequestBody.create(JSON,
        "{\n"
            + "    \"model\": \"text-davinci-003\",\n"
            + String.format(
                "    \"prompt\": \"Sort these logs on severity in deployment. If the message is incomplete info mark it least severe and if you cant decide for some messages, mark it as medium severe. Give the result as a json of List of Integers here where the integers represent the log message number sorted on severity on high to low. %s\"\n",
                logs)
            + "}");

    Request request = new Request.Builder()
                          .addHeader("Authorization", "Bearer " + apiToken)
                          .addHeader("Content-Type", "application/json")
                          .url(url)
                          .post(body)
                          .build();
    try (Response response = client.newCall(request).execute()) {
      String responseBody = response.body().string();
      ChatGPTCompletionsResponse chatGPTCompletionsResponse =
          gson.fromJson(responseBody, ChatGPTCompletionsResponse.class);
      processSeverity(chatGPTCompletionsResponse.getChoices().get(0).getText());
      return chatGPTCompletionsResponse.getChoices().get(0).getText();
    }
  }

  List<Integer> processSeverity(String str) {
    List<Integer> severityList = new ArrayList<>();

    str = str.replaceAll("[^0-9]", " ");
    str = str.replaceAll(" +", " ");
    Arrays.stream(str.split(" ")).forEach(s -> {
      if (!s.isEmpty() && s.charAt(0) >= '0' && s.charAt(0) <= '9') {
        severityList.add(Integer.parseInt(s));
      }
    });
    return severityList;
  }

  @Data
  @ToString
  public static class ChatGPTCompletionsResponse {
    String id;
    String object;
    long created;
    String model;
    List<Choices> choices;
    Usage usage;

    @Data
    @ToString
    public static class Choices {
      String text;
      String index;
      String logprobs;
      String finish_reason;
    }

    @Data
    @ToString
    public static class Usage {
      String prompt_tokens;
      String completion_tokens;
      String total_tokens;
    }
  }
}

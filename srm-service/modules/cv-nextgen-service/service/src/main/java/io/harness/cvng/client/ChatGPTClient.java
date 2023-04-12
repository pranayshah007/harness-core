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

  public String chatGPTResponse(String apiToken, String apiPath) throws IOException {
    String url = String.format("%s/%s", BASE_URL, apiPath);
    RequestBody body = RequestBody.create(JSON,
        "{\n"
            + "    \"model\": \"text-davinci-003\",\n"
            + "    \"prompt\": \"Say your name in style\"\n"
            + "}");

    Request request = new Request.Builder()
                          .addHeader("Authorization", "Bearer " + apiToken)
                          .addHeader("Content-Type", "application/json")
                          .url(url)
                          .post(body)
                          .build();
    try (Response response = client.newCall(request).execute()) {
      String responseBody = response.body().string();
      return responseBody;
    }
  }
}

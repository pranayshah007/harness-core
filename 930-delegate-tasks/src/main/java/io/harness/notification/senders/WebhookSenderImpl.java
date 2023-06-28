/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.senders;

import io.harness.delegate.beans.NotificationProcessingResponse;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Slf4j
public class WebhookSenderImpl {
  public static final MediaType APPLICATION_JSON = MediaType.parse("application/json; charset=utf-8");
  private final OkHttpClient client;

  public WebhookSenderImpl(OkHttpClient client) {
    this.client = client;
  }

  public WebhookSenderImpl() {
    client = new OkHttpClient();
  }

  public NotificationProcessingResponse send(List<String> webhookUrls, String message, String notificationId) {
    List<Boolean> results = new ArrayList<>();
    for (String webhookUrl : webhookUrls) {
      boolean ret = sendJSONMessage(message, webhookUrl);
      results.add(ret);
    }
    return NotificationProcessingResponse.builder().result(results).build();
  }

  private boolean sendJSONMessage(String message, String webhookWebhook) {
    try {
      RequestBody body = RequestBody.create(APPLICATION_JSON, message);
      Request request = new Request.Builder()
                            .url(webhookWebhook)
                            .post(body)
                            .addHeader("Content-Type", "application/json")
                            .addHeader("Accept", "*/*")
                            .build();

      try (Response response = client.newCall(request).execute()) {
        if (!response.isSuccessful()) {
          String bodyString = (null != response.body()) ? response.body().string() : "null";
          log.error("Response not Successful. Response body: {}", bodyString);
          return false;
        }
        return true;
      }
    } catch (Exception e) {
      log.error("Error sending post data", e);
    }
    return false;
  }
}

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.senders;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.apache.http.entity.mime.MIME.CONTENT_TYPE;

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
public class MSTeamsSenderImpl {
  private final OkHttpClient okHttpClient = new OkHttpClient.Builder().retryOnConnectionFailure(true).build();

  private static final MediaType APPLICATION_JSON_VALUE = MediaType.parse(APPLICATION_JSON);

  public int sendMessage(String message, String webhookUrl) {
    try {
      RequestBody body = RequestBody.create(APPLICATION_JSON_VALUE, message);
      Request request =
          new Request.Builder().url(webhookUrl).post(body).addHeader(CONTENT_TYPE, APPLICATION_JSON).build();
      try (Response response = okHttpClient.newCall(request).execute()) {
        if (!response.isSuccessful()) {
          String bodyString = (null != response.body()) ? response.body().string() : "null";
          log.error("Response not Successful. Response body: {}", bodyString);
        }
        return response.code();
      }
    } catch (Exception e) {
      log.error("Exception occurred at sendMessage(). Returning 400", e);
      return 400;
    }
  }

  public NotificationProcessingResponse send(
      List<String> microsoftTeamsWebhookUrls, String message, String notificationId) {
    List<Boolean> results = new ArrayList<>();
    for (String microsoftTeamsWebhookUrl : microsoftTeamsWebhookUrls) {
      int responseCode = sendMessage(message, microsoftTeamsWebhookUrl);
      results.add(responseCode >= 200 && responseCode < 300);
    }
    return NotificationProcessingResponse.builder().result(results).build();
  }
}

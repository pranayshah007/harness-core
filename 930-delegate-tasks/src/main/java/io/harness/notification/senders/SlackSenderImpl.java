/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.senders;

import io.harness.delegate.beans.NotificationProcessingResponse;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.notification.helper.NotificationSettingsHelper;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Slf4j
public class SlackSenderImpl {
  public static final MediaType APPLICATION_JSON = MediaType.parse("application/json; charset=utf-8");
  private final OkHttpClient client;
  @Inject private NotificationSettingsHelper notificationSettingsHelper;

  public SlackSenderImpl(OkHttpClient client, NotificationSettingsHelper notificationSettingsHelper) {
    this.client = client;
    this.notificationSettingsHelper = notificationSettingsHelper;
  }

  public SlackSenderImpl() {
    client = new OkHttpClient();
  }

  public NotificationProcessingResponse send(
      List<String> slackWebhookUrls, String message, String notificationId, String accountId) {
    slackWebhookUrls = notificationSettingsHelper.getRecipientsWithValidDomain(
        slackWebhookUrls, accountId, SettingIdentifiers.SLACK_NOTIFICATION_ENDPOINTS_ALLOWLIST);
    List<Boolean> results = new ArrayList<>();
    for (String webhookUrl : slackWebhookUrls) {
      boolean ret = sendJSONMessage(message, webhookUrl);
      results.add(ret);
    }
    return NotificationProcessingResponse.builder().result(results).build();
  }

  private boolean sendJSONMessage(String message, String slackWebhook) {
    try {
      RequestBody body = RequestBody.create(APPLICATION_JSON, message);
      Request request = new Request.Builder()
                            .url(slackWebhook)
                            .post(body)
                            .addHeader("Content-Type", "application/json")
                            .addHeader("Accept", "*/*")
                            .addHeader("Cache-Control", "no-cache")
                            .addHeader("Host", "hooks.slack.com")
                            .addHeader("accept-encoding", "gzip, deflate")
                            .addHeader("content-length", "798")
                            .addHeader("Connection", "keep-alive")
                            .addHeader("cache-control", "no-cache")
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

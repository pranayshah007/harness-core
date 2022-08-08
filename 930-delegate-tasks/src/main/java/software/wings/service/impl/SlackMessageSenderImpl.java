/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.common.NotificationConstants.SLACK_WEBHOOK_URL_PREFIX;
import static software.wings.common.NotificationConstants.WHITE_COLOR;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.network.Http;

import software.wings.beans.SlackMessage;
import software.wings.beans.SlackMessageJSON;
import software.wings.common.NotificationConstants;
import software.wings.service.intfc.SlackMessageSender;

import allbegray.slack.SlackClientFactory;
import allbegray.slack.type.Attachment;
import allbegray.slack.type.Payload;
import allbegray.slack.webhook.SlackWebhookClient;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.StringUtils;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Url;

@OwnedBy(CDC)
@Slf4j
public class SlackMessageSenderImpl implements SlackMessageSender {
  @Override
  public void send(SlackMessage slackMessage, boolean sendFromDelegate, boolean isCertValidationRequired) {
    String outgoingWebhookUrl = slackMessage.getOutgoingWebhookUrl();
    String slackChannel = slackMessage.getSlackChannel();
    String senderName = slackMessage.getSenderName();
    String message = slackMessage.getMessage();

    log.info("Slack sending message on channel: {}", slackChannel);

    Payload payload = new Payload();

    if (message.contains("||")) {
      Attachment attachment = new Attachment();
      String[] parts = message.split("\\|\\|");
      payload.setText(processText(parts[0]));
      attachment.setText(processText(parts[1]));
      attachment.setFooter(processText(parts[2]));
      attachment.setColor(NotificationConstants.getThemeColor(parts[3], WHITE_COLOR));
      attachment.setFooter_icon(format("https://s3.amazonaws.com/wings-assets/slackicons/%s.png", parts[3]));
      attachment.setMrkdwn_in(ImmutableList.of("text"));
      payload.setAttachments(ImmutableList.of(attachment));
    } else {
      payload.setText(processText(message));
    }

    if (isNotEmpty(slackChannel)) {
      if (slackChannel.charAt(0) != '#') {
        slackChannel = "#" + slackChannel;
      }
      payload.setChannel(slackChannel);
    }
    payload.setUsername(senderName);
    payload.setIcon_url("https://s3.amazonaws.com/wings-assets/slackicons/logo-slack.png");

    outgoingWebhookUrl = StringUtils.trim(outgoingWebhookUrl);
    // the SlackWebhookClient doesn't uses proxy. If you want to use the proxy on delegate, set
    // sendFromDelegate true.
    if (isSlackWebhookUrl(outgoingWebhookUrl) && !sendFromDelegate) {
      SlackWebhookClient webhookClient = getWebhookClient(outgoingWebhookUrl);
      webhookClient.post(payload);
    } else {
      sendGenericHttpPostRequest(outgoingWebhookUrl, payload, isCertValidationRequired);
    }
  }

  @Override
  public void sendJSON(SlackMessageJSON slackMessage) {
    OkHttpClient client = getHttpClient();
    try {
      Request slackRequest =
          SlackNotificationUtils.createHttpRequest(slackMessage.getMessage(), slackMessage.getOutgoingWebhookUrl());
      Response response = client.newCall(slackRequest).execute();
      if (!response.isSuccessful()) {
        String bodyString = (null != response.body()) ? response.body().string() : "null";

        log.error("Response not Successful. Response body: {}", bodyString);
        return;
      }
      log.info("Slack json message response was successful");
    } catch (Exception e) {
      log.error("Error sending post data", e);
    }
  }

  SlackWebhookClient getWebhookClient(final String webhookUrl) {
    return SlackClientFactory.createWebhookClient(webhookUrl);
  }

  OkHttpClient getHttpClient() {
    return new OkHttpClient();
  }

  private void sendGenericHttpPostRequest(String webhookUrl, Payload payload, boolean isCertValidationRequired) {
    if (webhookUrl.endsWith("/")) {
      webhookUrl = webhookUrl.substring(0, webhookUrl.length() - 1);
    }
    int lastIndexOf = webhookUrl.lastIndexOf('/') + 1;
    String baseUrl = webhookUrl.substring(0, lastIndexOf);
    String webhookToken = webhookUrl.substring(lastIndexOf);
    try {
      getSlackHttpClient(baseUrl, isCertValidationRequired).PostMsg(webhookToken, payload).execute();
    } catch (IOException e) {
      throw new InvalidRequestException("Post message failed", e);
    }
  }

  private boolean isSlackWebhookUrl(final String webhookUrl) {
    return webhookUrl.startsWith(SLACK_WEBHOOK_URL_PREFIX);
  }

  SlackHttpClient getSlackHttpClient(String baseUrl, boolean isCertValidationRequired) {
    final Retrofit retrofit = new Retrofit.Builder()
                                  .baseUrl(baseUrl)
                                  .addConverterFactory(JacksonConverterFactory.create())
                                  .client(Http.getOkHttpClient(baseUrl, isCertValidationRequired))
                                  .build();
    return retrofit.create(SlackHttpClient.class);
  }

  public interface SlackHttpClient {
    @POST Call<ResponseBody> PostMsg(@Url String url, @Body Payload payload);
  }

  private String processText(String message) {
    return message.replaceAll("<<<", "*<")
        .replaceAll("\\|-\\|", "|")
        .replaceAll(">>>", ">*")
        .replaceAll("\\\\n", "\n")
        .replaceAll("\\\\\\*", "*")
        .replaceAll("\\*<\\|>\\*", "");
  }
}

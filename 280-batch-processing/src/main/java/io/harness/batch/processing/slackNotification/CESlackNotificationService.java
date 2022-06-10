/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.slackNotification;

import allbegray.slack.SlackClientFactory;
import allbegray.slack.type.Attachment;
import allbegray.slack.type.Payload;
import allbegray.slack.webhook.SlackWebhookClient;
import com.google.common.collect.ImmutableList;
import io.harness.exception.InvalidRequestException;
import io.harness.network.Http;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;
import software.wings.beans.SlackMessage;
import software.wings.beans.notification.SlackNotificationConfiguration;
import software.wings.beans.notification.SlackNotificationSetting;
import software.wings.common.NotificationConstants;
import software.wings.service.impl.SlackMessageSenderImpl;
import software.wings.service.intfc.SlackMessageSender;

import com.google.inject.Singleton;

import java.io.IOException;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static java.lang.String.format;
import static software.wings.common.NotificationConstants.WHITE_COLOR;

@Slf4j
@Singleton
public class CESlackNotificationService {
  @Autowired private SlackMessageSender slackMessageSender;

  public static final String SLACK_WEBHOOK_URL_PREFIX = "https://hooks.slack.com/services/";

  public void sendMessage(SlackNotificationConfiguration slackConfig, String slackChannel, String senderName,
      String message, String accountId) {
    if (Objects.requireNonNull(slackConfig, "slack Config can't be null")
            .equals(SlackNotificationSetting.emptyConfig())) {
      return;
    }

    String webhookUrl = slackConfig.getOutgoingWebhookUrl();
    if (StringUtils.isEmpty(webhookUrl)) {
      log.error("Webhook URL is empty. No message will be sent. Config: {}, Message: {}", slackConfig, message);
      return;
    }

    log.info("Sending message for account {} via manager", accountId);

    send(
        new SlackMessage(
                slackConfig.getOutgoingWebhookUrl(),
                slackChannel,
                senderName,
                message),
            false,
            false);
  }

  private void send(SlackMessage slackMessage, boolean sendFromDelegate, boolean isCertValidationRequired) {
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

  SlackMessageSenderImpl.SlackHttpClient getSlackHttpClient(String baseUrl, boolean isCertValidationRequired) {
    final Retrofit retrofit = new Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(JacksonConverterFactory.create())
            .client(Http.getOkHttpClient(baseUrl, isCertValidationRequired))
            .build();
    return retrofit.create(SlackMessageSenderImpl.SlackHttpClient.class);
  }

  SlackWebhookClient getWebhookClient(final String webhookUrl) {
    return SlackClientFactory.createWebhookClient(webhookUrl);
  }

  private boolean isSlackWebhookUrl(final String webhookUrl) {
    return webhookUrl.startsWith(SLACK_WEBHOOK_URL_PREFIX);
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

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.notification.service;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.ANKUSH;
import static io.harness.rule.OwnerRule.VUK;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.data.algorithm.HashGenerator;
import io.harness.delegate.beans.NotificationProcessingResponse;
import io.harness.delegate.beans.NotificationTaskResponse;
import io.harness.notification.NotificationRequest;
import io.harness.notification.exception.NotificationException;
import io.harness.notification.remote.dto.NotificationSettingDTO;
import io.harness.notification.remote.dto.SlackSettingDTO;
import io.harness.notification.senders.SlackSenderImpl;
import io.harness.notification.service.api.NotificationSettingsService;
import io.harness.notification.service.api.NotificationTemplateService;
import io.harness.rule.Owner;
import io.harness.service.DelegateGrpcClientWrapper;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import lombok.SneakyThrows;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PL)
public class SlackServiceImplTest extends CategoryTest {
  @Mock private NotificationSettingsService notificationSettingsService;
  @Mock private NotificationTemplateService notificationTemplateService;
  @Mock private SlackSenderImpl slackSender;
  @Mock private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  private SlackServiceImpl slackService;
  private String accountId = "accountId";
  private String slackTemplateName = "slack_test";
  private String slackWebhookurl = "slack-webhookurl";
  private String slackWebhookurl2 = "slack-webhookurl2";
  private String id = "id";

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    slackService = new SlackServiceImpl(
        notificationSettingsService, notificationTemplateService, slackSender, delegateGrpcClientWrapper);
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void sendNotification_EmptyRequest() {
    NotificationRequest notificationRequest = NotificationRequest.newBuilder().build();
    NotificationProcessingResponse notificationProcessingResponse = slackService.send(notificationRequest);
    assertTrue(notificationProcessingResponse.equals(NotificationProcessingResponse.trivialResponseWithNoRetries));
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void sendNotification_OnlyIdInRequest() {
    NotificationRequest notificationRequest = NotificationRequest.newBuilder().setId(id).build();
    NotificationProcessingResponse notificationProcessingResponse = slackService.send(notificationRequest);
    assertTrue(notificationProcessingResponse.equals(NotificationProcessingResponse.trivialResponseWithNoRetries));
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void sendNotification_NoTemplateIdInRequest() {
    NotificationRequest notificationRequest =
        NotificationRequest.newBuilder()
            .setId(id)
            .setAccountId(accountId)
            .setSlack(NotificationRequest.Slack.newBuilder()
                          .addAllSlackWebHookUrls(Collections.singletonList(slackWebhookurl))
                          .build())
            .build();
    NotificationProcessingResponse notificationProcessingResponse = slackService.send(notificationRequest);
    assertTrue(notificationProcessingResponse.equals(NotificationProcessingResponse.trivialResponseWithNoRetries));
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void sendNotification_NoRecipientInRequest() {
    NotificationRequest notificationRequest = NotificationRequest.newBuilder()
                                                  .setId(id)
                                                  .setAccountId(accountId)
                                                  .setSlack(NotificationRequest.Slack.newBuilder()
                                                                .setTemplateId(slackTemplateName)
                                                                .addAllSlackWebHookUrls(Collections.EMPTY_LIST)
                                                                .build())
                                                  .build();
    NotificationProcessingResponse notificationProcessingResponse = slackService.send(notificationRequest);
    assertTrue(notificationProcessingResponse.equals(NotificationProcessingResponse.trivialResponseWithNoRetries));
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void sendNotification_ValidCaseSlackUserGroup() {
    NotificationRequest notificationRequest =
        NotificationRequest.newBuilder()
            .setId(id)
            .setAccountId(accountId)
            .setSlack(NotificationRequest.Slack.newBuilder()
                          .addAllSlackWebHookUrls(Collections.singletonList(slackWebhookurl))
                          .setTemplateId(slackTemplateName)
                          .addUserGroup(NotificationRequest.UserGroup.newBuilder()
                                            .setIdentifier("identifier")
                                            .setProjectIdentifier("projectIdentifier")
                                            .setOrgIdentifier("orgIdentifier")
                                            .build())
                          .build())
            .build();

    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(Arrays.asList(true, false)).shouldRetry(false).build();
    when(notificationTemplateService.getTemplateAsString(eq(slackTemplateName), any()))
        .thenReturn(Optional.empty(), Optional.of("This is a test notification"));
    when(notificationSettingsService.getSendNotificationViaDelegate(eq(accountId))).thenReturn(false);
    when(slackSender.send(any(), any(), any())).thenReturn(notificationExpectedResponse);

    NotificationProcessingResponse notificationProcessingResponse = slackService.send(notificationRequest);
    assertEquals(notificationProcessingResponse, NotificationProcessingResponse.trivialResponseWithNoRetries);

    notificationProcessingResponse = slackService.send(notificationRequest);
    assertEquals(notificationExpectedResponse, notificationProcessingResponse);

    notificationRequest = NotificationRequest.newBuilder()
                              .setId(id)
                              .setAccountId(accountId)
                              .setSlack(NotificationRequest.Slack.newBuilder()
                                            .setTemplateId(slackTemplateName)
                                            .addAllSlackWebHookUrls(Collections.singletonList(slackWebhookurl))
                                            .addUserGroup(NotificationRequest.UserGroup.newBuilder()
                                                              .setIdentifier("identifier")
                                                              .setProjectIdentifier("projectIdentifier")
                                                              .setOrgIdentifier("orgIdentifier")
                                                              .build())
                                            .build())
                              .build();
    notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(Arrays.asList(true, false)).shouldRetry(false).build();
    when(notificationTemplateService.getTemplateAsString(eq(slackTemplateName), any()))
        .thenReturn(Optional.of("this is test notification"));
    when(notificationSettingsService.getSendNotificationViaDelegate(eq(accountId))).thenReturn(true);
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(NotificationTaskResponse.builder().processingResponse(notificationExpectedResponse).build());
    notificationProcessingResponse = slackService.send(notificationRequest);
    assertEquals(notificationExpectedResponse, notificationProcessingResponse);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void sendNotification_WhenSecretIsPassedForWebhookURl() {
    NotificationRequest notificationRequest =
        NotificationRequest.newBuilder()
            .setId(id)
            .setAccountId(accountId)
            .setSlack(NotificationRequest.Slack.newBuilder()
                          .addAllSlackWebHookUrls(Collections.singletonList(slackWebhookurl))
                          .setTemplateId(slackTemplateName)
                          .setExpressionFunctorToken(HashGenerator.generateIntegerHash())
                          .setOrgIdentifier("orgIdentifier")
                          .setProjectIdentifier("projectIdentifier")
                          .build())
            .build();

    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(Arrays.asList(true, false)).shouldRetry(false).build();
    when(notificationTemplateService.getTemplateAsString(eq(slackTemplateName), any()))
        .thenReturn(Optional.empty(), Optional.of("This is a test notification"));
    when(notificationSettingsService.getSendNotificationViaDelegate(eq(accountId))).thenReturn(false);
    when(slackSender.send(any(), any(), any())).thenReturn(notificationExpectedResponse);

    NotificationProcessingResponse notificationProcessingResponse = slackService.send(notificationRequest);
    assertEquals(notificationProcessingResponse, NotificationProcessingResponse.trivialResponseWithNoRetries);

    notificationProcessingResponse = slackService.send(notificationRequest);
    assertEquals(notificationExpectedResponse, notificationProcessingResponse);

    notificationRequest = NotificationRequest.newBuilder()
                              .setId(id)
                              .setAccountId(accountId)
                              .setSlack(NotificationRequest.Slack.newBuilder()
                                            .setTemplateId(slackTemplateName)
                                            .addAllSlackWebHookUrls(Arrays.asList(slackWebhookurl, slackWebhookurl2))
                                            .setExpressionFunctorToken(HashGenerator.generateIntegerHash())
                                            .setOrgIdentifier("orgIdentifier")
                                            .setProjectIdentifier("projectIdentifier")
                                            .build())
                              .build();
    notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(Arrays.asList(true, false)).shouldRetry(false).build();
    when(notificationTemplateService.getTemplateAsString(eq(slackTemplateName), any()))
        .thenReturn(Optional.of("this is test notification"));
    when(notificationSettingsService.getSendNotificationViaDelegate(eq(accountId))).thenReturn(true);
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(NotificationTaskResponse.builder().processingResponse(notificationExpectedResponse).build());
    notificationProcessingResponse = slackService.send(notificationRequest);
    assertEquals(notificationExpectedResponse, notificationProcessingResponse);
  }

  @SneakyThrows
  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void sendNotification_ValidCase() {
    NotificationRequest notificationRequest =
        NotificationRequest.newBuilder()
            .setId(id)
            .setAccountId(accountId)
            .setSlack(NotificationRequest.Slack.newBuilder()
                          .setTemplateId(slackTemplateName)
                          .addAllSlackWebHookUrls(Collections.singletonList(slackWebhookurl))
                          .build())
            .build();
    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(Arrays.asList(true, false)).shouldRetry(false).build();
    when(notificationTemplateService.getTemplateAsString(eq(slackTemplateName), any()))
        .thenReturn(Optional.empty(), Optional.of("This is a test notification"));
    when(notificationSettingsService.getSendNotificationViaDelegate(eq(accountId))).thenReturn(false);
    when(slackSender.send(any(), any(), any())).thenReturn(notificationExpectedResponse);

    NotificationProcessingResponse notificationProcessingResponse = slackService.send(notificationRequest);
    assertTrue(notificationProcessingResponse.equals(NotificationProcessingResponse.trivialResponseWithNoRetries));

    notificationProcessingResponse = slackService.send(notificationRequest);
    assertEquals(notificationExpectedResponse, notificationProcessingResponse);

    notificationRequest = NotificationRequest.newBuilder()
                              .setId(id)
                              .setAccountId(accountId)
                              .setSlack(NotificationRequest.Slack.newBuilder()
                                            .setTemplateId(slackTemplateName)
                                            .addAllSlackWebHookUrls(Collections.singletonList(slackWebhookurl))
                                            .build())
                              .build();
    notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(Arrays.asList(true, false)).shouldRetry(false).build();
    when(notificationTemplateService.getTemplateAsString(eq(slackTemplateName), any()))
        .thenReturn(Optional.of("this is test notification"));
    when(notificationSettingsService.getSendNotificationViaDelegate(eq(accountId))).thenReturn(true);
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(NotificationTaskResponse.builder().processingResponse(notificationExpectedResponse).build());
    notificationProcessingResponse = slackService.send(notificationRequest);
    assertEquals(notificationExpectedResponse, notificationProcessingResponse);
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void sendTestNotification_CheckAllGaurds() {
    final NotificationSettingDTO notificationSettingDTO1 = SlackSettingDTO.builder().build();
    assertThatThrownBy(() -> slackService.sendTestNotification(notificationSettingDTO1))
        .isInstanceOf(NotificationException.class);

    final NotificationSettingDTO notificationSettingDTO2 = SlackSettingDTO.builder().accountId(accountId).build();
    assertThatThrownBy(() -> slackService.sendTestNotification(notificationSettingDTO2))
        .isInstanceOf(NotificationException.class);

    final NotificationSettingDTO notificationSettingDTO3 =
        SlackSettingDTO.builder().recipient("email@harness.io").build();
    assertThatThrownBy(() -> slackService.sendTestNotification(notificationSettingDTO3))
        .isInstanceOf(NotificationException.class);
  }

  @SneakyThrows
  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void sendTestNotification_ValidRequest() {
    final NotificationSettingDTO notificationSettingDTO4 =
        SlackSettingDTO.builder().accountId(accountId).recipient(slackWebhookurl).build();
    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(Arrays.asList(true)).shouldRetry(false).build();
    when(slackSender.send(any(), any(), any())).thenReturn(notificationExpectedResponse);
    when(notificationTemplateService.getTemplateAsString(any(), any()))
        .thenReturn(Optional.of("This is a test notification"));
    boolean response = slackService.sendTestNotification(notificationSettingDTO4);
    assertTrue(response);
  }
}

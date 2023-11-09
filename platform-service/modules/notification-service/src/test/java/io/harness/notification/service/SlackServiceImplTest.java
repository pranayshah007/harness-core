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
import static io.harness.rule.OwnerRule.ASHISHSANODIA;
import static io.harness.rule.OwnerRule.VUK;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.category.element.UnitTests;
import io.harness.data.algorithm.HashGenerator;
import io.harness.delegate.beans.NotificationProcessingResponse;
import io.harness.delegate.beans.NotificationTaskResponse;
import io.harness.delegate.beans.SlackTaskParams;
import io.harness.notification.NotificationRequest;
import io.harness.notification.exception.NotificationException;
import io.harness.notification.remote.dto.NotificationSettingDTO;
import io.harness.notification.remote.dto.SlackSettingDTO;
import io.harness.notification.senders.SlackSenderImpl;
import io.harness.notification.service.api.NotificationSettingsService;
import io.harness.notification.service.api.NotificationTemplateService;
import io.harness.notification.utils.NotificationSettingsHelper;
import io.harness.rule.Owner;
import io.harness.service.DelegateGrpcClientWrapper;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.SneakyThrows;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PL)
public class SlackServiceImplTest extends CategoryTest {
  private static final String TEST_NOTIFICATION_TEMPLATE_CONTENT = "This is a test notification";
  @Mock private NotificationSettingsService notificationSettingsService;
  @Mock private NotificationTemplateService notificationTemplateService;
  @Mock private SlackSenderImpl slackSender;
  @Mock private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Mock private NotificationSettingsHelper notificationSettingsHelper;
  private SlackServiceImpl slackService;
  private String accountId = "accountId";
  private String slackTemplateName = "slack_test";
  private String slackWebhookurl = "slack-webhookurl";
  private String slackWebhookurl2 = "slack-webhookurl2";
  private String id = "id";

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    slackService = new SlackServiceImpl(notificationSettingsService, notificationTemplateService, slackSender,
        delegateGrpcClientWrapper, notificationSettingsHelper);
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
        .thenReturn(Optional.empty(), Optional.of(TEST_NOTIFICATION_TEMPLATE_CONTENT));
    when(notificationSettingsService.getSendNotificationViaDelegate(eq(accountId))).thenReturn(false);
    when(slackSender.send(any(), any(), any(), any())).thenReturn(notificationExpectedResponse);

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
        .thenReturn(Optional.empty(), Optional.of(TEST_NOTIFICATION_TEMPLATE_CONTENT));
    when(notificationSettingsService.getSendNotificationViaDelegate(eq(accountId))).thenReturn(false);
    when(slackSender.send(any(), any(), any(), any())).thenReturn(notificationExpectedResponse);

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
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void sendNotification_ValidCase_WithoutTemplateId_WithCustomContent() {
    NotificationRequest.Slack slackBuilder = NotificationRequest.Slack.newBuilder()
                                                 .addAllSlackWebHookUrls(Collections.singletonList(slackWebhookurl))
                                                 .setMessage("some-message")
                                                 .build();
    NotificationRequest notificationRequest =
        NotificationRequest.newBuilder().setId(id).setAccountId(accountId).setSlack(slackBuilder).build();
    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(List.of(true)).shouldRetry(false).build();
    when(notificationSettingsService.checkIfWebhookIsSecret(any())).thenReturn(false);
    when(slackSender.send(any(), any(), any(), any())).thenReturn(notificationExpectedResponse);

    NotificationProcessingResponse notificationProcessingResponse = slackService.send(notificationRequest);

    verifyNoInteractions(notificationTemplateService);
    verify(slackSender, times(1)).send(eq(List.of(slackWebhookurl)), eq(slackBuilder.getMessage()), any(), any());
    assertEquals(notificationExpectedResponse, notificationProcessingResponse);
  }

  @SneakyThrows
  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void sendNotification_ValidCase_WithoutDelegate() {
    NotificationRequest.Slack slackBuilder = NotificationRequest.Slack.newBuilder()
                                                 .setTemplateId(slackTemplateName)
                                                 .addAllSlackWebHookUrls(Collections.singletonList(slackWebhookurl))
                                                 .build();
    NotificationRequest notificationRequest =
        NotificationRequest.newBuilder().setId(id).setAccountId(accountId).setSlack(slackBuilder).build();
    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(List.of(true)).shouldRetry(false).build();
    when(notificationTemplateService.getTemplateAsString(eq(slackTemplateName), any()))
        .thenReturn(Optional.of(TEST_NOTIFICATION_TEMPLATE_CONTENT));
    when(notificationSettingsService.checkIfWebhookIsSecret(any())).thenReturn(false);
    when(slackSender.send(any(), any(), any(), any())).thenReturn(notificationExpectedResponse);

    NotificationProcessingResponse notificationProcessingResponse = slackService.send(notificationRequest);

    verify(slackSender, times(1))
        .send(eq(List.of(slackWebhookurl)), eq(TEST_NOTIFICATION_TEMPLATE_CONTENT), any(), any());
    assertEquals(notificationExpectedResponse, notificationProcessingResponse);
  }

  @SneakyThrows
  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void sendNotification_ValidCase_WithDelegate() {
    NotificationRequest.Slack slackBuilder = NotificationRequest.Slack.newBuilder()
                                                 .setTemplateId(slackTemplateName)
                                                 .addAllSlackWebHookUrls(Collections.singletonList(slackWebhookurl))
                                                 .build();
    NotificationRequest notificationRequest =
        NotificationRequest.newBuilder().setId(id).setAccountId(accountId).setSlack(slackBuilder).build();
    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(List.of(true)).shouldRetry(false).build();
    when(notificationTemplateService.getTemplateAsString(eq(slackTemplateName), any()))
        .thenReturn(Optional.of(TEST_NOTIFICATION_TEMPLATE_CONTENT));
    when(notificationSettingsService.checkIfWebhookIsSecret(any())).thenReturn(true);
    when(delegateGrpcClientWrapper.submitAsyncTaskV2(any(), any())).thenReturn("");

    NotificationProcessingResponse notificationProcessingResponse = slackService.send(notificationRequest);

    ArgumentCaptor<DelegateTaskRequest> delegateTaskRequestArgumentCaptor =
        ArgumentCaptor.forClass(DelegateTaskRequest.class);
    verify(delegateGrpcClientWrapper, times(1)).submitAsyncTaskV2(delegateTaskRequestArgumentCaptor.capture(), any());

    DelegateTaskRequest actualDelegateTaskRequest = delegateTaskRequestArgumentCaptor.getValue();
    SlackTaskParams slackTaskParams = (SlackTaskParams) actualDelegateTaskRequest.getTaskParameters();

    assertThat(slackTaskParams.getSlackWebhookUrls()).isNotEmpty();
    assertThat(slackTaskParams.getSlackWebhookUrls()).hasSize(1);
    assertThat(slackTaskParams.getSlackWebhookUrls()).contains(slackWebhookurl);
    assertThat(slackTaskParams.getMessage()).isEqualTo(TEST_NOTIFICATION_TEMPLATE_CONTENT);
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
    when(slackSender.send(any(), any(), any(), any())).thenReturn(notificationExpectedResponse);
    when(notificationTemplateService.getTemplateAsString(any(), any()))
        .thenReturn(Optional.of(TEST_NOTIFICATION_TEMPLATE_CONTENT));
    boolean response = slackService.sendTestNotification(notificationSettingDTO4);
    assertTrue(response);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void sendSyncNotification_EmptyRequest() {
    NotificationRequest notificationRequest = NotificationRequest.newBuilder().build();

    assertThatThrownBy(() -> slackService.sendSync(notificationRequest))
        .isInstanceOf(NotificationException.class)
        .hasMessage("Invalid slack notification request");
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void sendSyncNotification_OnlyIdInRequest() {
    NotificationRequest notificationRequest = NotificationRequest.newBuilder().setId(id).build();

    assertThatThrownBy(() -> slackService.sendSync(notificationRequest))
        .isInstanceOf(NotificationException.class)
        .hasMessage("Invalid slack notification request");
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void sendSyncNotification_NoAccountIdInRequest() {
    NotificationRequest notificationRequest =
        NotificationRequest.newBuilder().setId(id).setSlack(NotificationRequest.Slack.newBuilder().build()).build();

    assertThatThrownBy(() -> slackService.sendSync(notificationRequest))
        .isInstanceOf(NotificationException.class)
        .hasMessageContaining("No account id encountered for");
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void sendSyncNotification_NoRecipientInRequest() {
    NotificationRequest notificationRequest = NotificationRequest.newBuilder()
                                                  .setId(id)
                                                  .setAccountId(accountId)
                                                  .setSlack(NotificationRequest.Slack.newBuilder()
                                                                .setTemplateId(slackTemplateName)
                                                                .addAllSlackWebHookUrls(Collections.EMPTY_LIST)
                                                                .build())
                                                  .build();

    assertThatThrownBy(() -> slackService.sendSync(notificationRequest))
        .isInstanceOf(NotificationException.class)
        .hasMessageContaining("No slackWebhookUrls found in notification request");
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void sendSyncNotification_NonExistingTemplateInRequest() {
    NotificationRequest notificationRequest =
        NotificationRequest.newBuilder()
            .setId(id)
            .setAccountId(accountId)
            .setSlack(NotificationRequest.Slack.newBuilder()
                          .setTemplateId(slackTemplateName)
                          .addAllSlackWebHookUrls(Collections.singletonList(slackWebhookurl))
                          .build())
            .build();
    when(notificationTemplateService.getTemplateAsString(eq(slackTemplateName), any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> slackService.sendSync(notificationRequest))
        .isInstanceOf(NotificationException.class)
        .hasMessageContaining("Failed to send notification request")
        .hasMessageContaining("possibly due to no valid template with name");
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void sendSyncNotification_ValidCase_WithoutTemplateIdInRequest() {
    NotificationRequest.Slack slackBuilder = NotificationRequest.Slack.newBuilder()
                                                 .addAllSlackWebHookUrls(Collections.singletonList(slackWebhookurl))
                                                 .setMessage("custom-message")
                                                 .build();
    NotificationRequest notificationRequest =
        NotificationRequest.newBuilder().setId(id).setAccountId(accountId).setSlack(slackBuilder).build();

    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(Arrays.asList(true)).shouldRetry(false).build();
    when(notificationTemplateService.getTemplateAsString(eq(slackTemplateName), any()))
        .thenReturn(Optional.of(TEST_NOTIFICATION_TEMPLATE_CONTENT));
    when(notificationSettingsService.checkIfWebhookIsSecret(any())).thenReturn(true);
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(NotificationTaskResponse.builder().processingResponse(notificationExpectedResponse).build());
    NotificationTaskResponse notificationTaskResponse = slackService.sendSync(notificationRequest);

    verifyNoInteractions(notificationTemplateService);
    ArgumentCaptor<DelegateTaskRequest> delegateTaskRequestArgumentCaptor =
        ArgumentCaptor.forClass(DelegateTaskRequest.class);
    verify(delegateGrpcClientWrapper, times(1)).executeSyncTaskV2(delegateTaskRequestArgumentCaptor.capture());

    DelegateTaskRequest actualDelegateTaskRequest = delegateTaskRequestArgumentCaptor.getValue();
    SlackTaskParams slackTaskParams = (SlackTaskParams) actualDelegateTaskRequest.getTaskParameters();

    assertThat(slackTaskParams.getSlackWebhookUrls()).isNotEmpty();
    assertThat(slackTaskParams.getSlackWebhookUrls()).hasSize(1);
    assertThat(slackTaskParams.getSlackWebhookUrls()).contains(slackWebhookurl);
    assertEquals(slackBuilder.getMessage(), slackTaskParams.getMessage());
    assertEquals(notificationExpectedResponse, notificationTaskResponse.getProcessingResponse());
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void sendSyncNotification_ValidCase_WithoutDelegate() {
    NotificationRequest.Slack slackBuilder = NotificationRequest.Slack.newBuilder()
                                                 .addAllSlackWebHookUrls(Collections.singletonList(slackWebhookurl))
                                                 .setTemplateId(slackTemplateName)
                                                 .setMessage("custom-message")
                                                 .build();
    NotificationRequest notificationRequest =
        NotificationRequest.newBuilder().setId(id).setAccountId(accountId).setSlack(slackBuilder).build();

    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(Arrays.asList(true)).shouldRetry(false).build();
    when(notificationTemplateService.getTemplateAsString(eq(slackTemplateName), any()))
        .thenReturn(Optional.of(TEST_NOTIFICATION_TEMPLATE_CONTENT));
    when(notificationSettingsService.checkIfWebhookIsSecret(any())).thenReturn(false);
    when(slackSender.send(any(), any(), any(), any())).thenReturn(notificationExpectedResponse);

    NotificationTaskResponse notificationTaskResponse = slackService.sendSync(notificationRequest);

    verify(slackSender, times(1)).send(anyList(), eq(TEST_NOTIFICATION_TEMPLATE_CONTENT), anyString(), anyList());

    assertEquals(notificationExpectedResponse, notificationTaskResponse.getProcessingResponse());
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void sendSyncNotification_ValidCase_UsingDelegate() {
    NotificationRequest.Slack slackBuilder = NotificationRequest.Slack.newBuilder()
                                                 .addAllSlackWebHookUrls(Collections.singletonList(slackWebhookurl))
                                                 .setTemplateId(slackTemplateName)
                                                 .setMessage("custom-message")
                                                 .build();
    NotificationRequest notificationRequest =
        NotificationRequest.newBuilder().setId(id).setAccountId(accountId).setSlack(slackBuilder).build();

    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(Arrays.asList(true)).shouldRetry(false).build();
    when(notificationTemplateService.getTemplateAsString(eq(slackTemplateName), any()))
        .thenReturn(Optional.of(TEST_NOTIFICATION_TEMPLATE_CONTENT));
    when(notificationSettingsService.checkIfWebhookIsSecret(any())).thenReturn(true);
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(NotificationTaskResponse.builder().processingResponse(notificationExpectedResponse).build());

    NotificationTaskResponse notificationTaskResponse = slackService.sendSync(notificationRequest);

    ArgumentCaptor<DelegateTaskRequest> delegateTaskRequestArgumentCaptor =
        ArgumentCaptor.forClass(DelegateTaskRequest.class);
    verify(delegateGrpcClientWrapper, times(1)).executeSyncTaskV2(delegateTaskRequestArgumentCaptor.capture());

    DelegateTaskRequest actualDelegateTaskRequest = delegateTaskRequestArgumentCaptor.getValue();
    SlackTaskParams slackTaskParams = (SlackTaskParams) actualDelegateTaskRequest.getTaskParameters();

    assertThat(slackTaskParams.getSlackWebhookUrls()).isNotEmpty();
    assertThat(slackTaskParams.getSlackWebhookUrls()).hasSize(1);
    assertThat(slackTaskParams.getSlackWebhookUrls()).contains(slackWebhookurl);
    assertEquals(TEST_NOTIFICATION_TEMPLATE_CONTENT, slackTaskParams.getMessage());
    assertEquals(notificationExpectedResponse, notificationTaskResponse.getProcessingResponse());
  }
}

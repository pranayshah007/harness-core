/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.notification.service;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.ASHISHSANODIA;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
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
import io.harness.delegate.beans.WebhookTaskParams;
import io.harness.notification.NotificationRequest;
import io.harness.notification.exception.NotificationException;
import io.harness.notification.senders.WebhookSenderImpl;
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
public class WebhookServiceImplTest extends CategoryTest {
  private static final String TEST_NOTIFICATION_TEMPLATE_CONTENT = "This is a test notification";
  private final String accountId = "accountId";
  private final String webhookTemplateName = "webhook_test";
  private final String webhookUrl = "webhook-url";
  private final String webhookUrl2 = "webhook-url2";
  private final String id = "id";

  @Mock private NotificationSettingsService notificationSettingsService;
  @Mock private NotificationTemplateService notificationTemplateService;
  @Mock private WebhookSenderImpl webhookSender;
  @Mock private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Mock private NotificationSettingsHelper notificationSettingsHelper;
  private WebhookServiceImpl webhookService;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    webhookService = new WebhookServiceImpl(notificationSettingsService, notificationTemplateService, webhookSender,
        delegateGrpcClientWrapper, notificationSettingsHelper);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void sendNotification_EmptyRequest() {
    NotificationRequest notificationRequest = NotificationRequest.newBuilder().build();
    NotificationProcessingResponse notificationProcessingResponse = webhookService.send(notificationRequest);
    assertTrue(notificationProcessingResponse.equals(NotificationProcessingResponse.trivialResponseWithNoRetries));
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void sendNotification_OnlyIdInRequest() {
    NotificationRequest notificationRequest = NotificationRequest.newBuilder().setId(id).build();
    NotificationProcessingResponse notificationProcessingResponse = webhookService.send(notificationRequest);
    assertTrue(notificationProcessingResponse.equals(NotificationProcessingResponse.trivialResponseWithNoRetries));
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void sendNotification_NoRecipientInRequest() {
    NotificationRequest notificationRequest = NotificationRequest.newBuilder()
                                                  .setId(id)
                                                  .setAccountId(accountId)
                                                  .setWebhook(NotificationRequest.Webhook.newBuilder()
                                                                  .setTemplateId(webhookTemplateName)
                                                                  .addAllUrls(Collections.EMPTY_LIST)
                                                                  .build())
                                                  .build();
    NotificationProcessingResponse notificationProcessingResponse = webhookService.send(notificationRequest);
    assertTrue(notificationProcessingResponse.equals(NotificationProcessingResponse.trivialResponseWithNoRetries));
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void sendNotification_ValidCaseWebhookUserGroup() {
    NotificationRequest notificationRequest =
        NotificationRequest.newBuilder()
            .setId(id)
            .setAccountId(accountId)
            .setWebhook(NotificationRequest.Webhook.newBuilder()
                            .addAllUrls(Collections.singletonList(webhookUrl))
                            .setTemplateId(webhookTemplateName)
                            .addUserGroup(NotificationRequest.UserGroup.newBuilder()
                                              .setIdentifier("identifier")
                                              .setProjectIdentifier("projectIdentifier")
                                              .setOrgIdentifier("orgIdentifier")
                                              .build())
                            .build())
            .build();

    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(Arrays.asList(true, false)).shouldRetry(false).build();
    when(notificationTemplateService.getTemplateAsString(eq(webhookTemplateName), any()))
        .thenReturn(Optional.empty(), Optional.of(TEST_NOTIFICATION_TEMPLATE_CONTENT));
    when(notificationSettingsService.getSendNotificationViaDelegate(eq(accountId))).thenReturn(false);
    when(webhookSender.send(any(), any(), any(), any(), any())).thenReturn(notificationExpectedResponse);

    NotificationProcessingResponse notificationProcessingResponse = webhookService.send(notificationRequest);
    assertEquals(notificationProcessingResponse, NotificationProcessingResponse.trivialResponseWithNoRetries);

    notificationProcessingResponse = webhookService.send(notificationRequest);
    assertEquals(notificationExpectedResponse, notificationProcessingResponse);

    notificationRequest = NotificationRequest.newBuilder()
                              .setId(id)
                              .setAccountId(accountId)
                              .setWebhook(NotificationRequest.Webhook.newBuilder()
                                              .setTemplateId(webhookTemplateName)
                                              .addAllUrls(Collections.singletonList(webhookUrl))
                                              .addUserGroup(NotificationRequest.UserGroup.newBuilder()
                                                                .setIdentifier("identifier")
                                                                .setProjectIdentifier("projectIdentifier")
                                                                .setOrgIdentifier("orgIdentifier")
                                                                .build())
                                              .build())
                              .build();
    notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(Arrays.asList(true, false)).shouldRetry(false).build();
    when(notificationTemplateService.getTemplateAsString(eq(webhookTemplateName), any()))
        .thenReturn(Optional.of(TEST_NOTIFICATION_TEMPLATE_CONTENT));
    when(notificationSettingsService.getSendNotificationViaDelegate(eq(accountId))).thenReturn(true);
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(NotificationTaskResponse.builder().processingResponse(notificationExpectedResponse).build());
    notificationProcessingResponse = webhookService.send(notificationRequest);
    assertEquals(notificationExpectedResponse, notificationProcessingResponse);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void sendNotification_WhenSecretIsPassedForWebhookURl() {
    NotificationRequest notificationRequest =
        NotificationRequest.newBuilder()
            .setId(id)
            .setAccountId(accountId)
            .setWebhook(NotificationRequest.Webhook.newBuilder()
                            .addAllUrls(Collections.singletonList(webhookUrl))
                            .setTemplateId(webhookTemplateName)
                            .setExpressionFunctorToken(HashGenerator.generateIntegerHash())
                            .setOrgIdentifier("orgIdentifier")
                            .setProjectIdentifier("projectIdentifier")
                            .build())
            .build();

    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(Arrays.asList(true, false)).shouldRetry(false).build();
    when(notificationTemplateService.getTemplateAsString(eq(webhookTemplateName), any()))
        .thenReturn(Optional.empty(), Optional.of(TEST_NOTIFICATION_TEMPLATE_CONTENT));
    when(notificationSettingsService.checkIfWebhookIsSecret(any())).thenReturn(false);
    when(webhookSender.send(any(), any(), any(), any(), any())).thenReturn(notificationExpectedResponse);

    NotificationProcessingResponse notificationProcessingResponse = webhookService.send(notificationRequest);
    assertEquals(notificationProcessingResponse, NotificationProcessingResponse.trivialResponseWithNoRetries);

    notificationProcessingResponse = webhookService.send(notificationRequest);
    assertEquals(notificationExpectedResponse, notificationProcessingResponse);

    notificationRequest = NotificationRequest.newBuilder()
                              .setId(id)
                              .setAccountId(accountId)
                              .setWebhook(NotificationRequest.Webhook.newBuilder()
                                              .setTemplateId(webhookTemplateName)
                                              .addAllUrls(List.of(webhookUrl, webhookUrl2))
                                              .setExpressionFunctorToken(HashGenerator.generateIntegerHash())
                                              .setOrgIdentifier("orgIdentifier")
                                              .setProjectIdentifier("projectIdentifier")
                                              .build())
                              .build();
    notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(Arrays.asList(true, true)).shouldRetry(false).build();
    when(notificationTemplateService.getTemplateAsString(eq(webhookTemplateName), any()))
        .thenReturn(Optional.of(TEST_NOTIFICATION_TEMPLATE_CONTENT));
    when(notificationSettingsService.checkIfWebhookIsSecret(any())).thenReturn(true);
    when(delegateGrpcClientWrapper.submitAsyncTaskV2(any(), any())).thenReturn("task-id");
    notificationProcessingResponse = webhookService.send(notificationRequest);
    assertEquals(notificationExpectedResponse, notificationProcessingResponse);
  }

  @SneakyThrows
  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void sendNotification_ValidCase_WithoutTemplateId_WithCustomContent() {
    NotificationRequest.Webhook webhookBuilder = NotificationRequest.Webhook.newBuilder()
                                                     .addAllUrls(Collections.singletonList(webhookUrl))
                                                     .setMessage("some-message")
                                                     .build();
    NotificationRequest notificationRequest =
        NotificationRequest.newBuilder().setId(id).setAccountId(accountId).setWebhook(webhookBuilder).build();
    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(List.of(true)).shouldRetry(false).build();
    when(notificationSettingsService.checkIfWebhookIsSecret(any())).thenReturn(false);
    when(webhookSender.send(any(), any(), any(), any(), any())).thenReturn(notificationExpectedResponse);

    NotificationProcessingResponse notificationProcessingResponse = webhookService.send(notificationRequest);

    verifyNoInteractions(notificationTemplateService);
    verify(webhookSender, times(1))
        .send(eq(List.of(webhookUrl)), eq(webhookBuilder.getMessage()), anyString(), anyMap(), anyList());
    assertEquals(notificationExpectedResponse, notificationProcessingResponse);
  }

  @SneakyThrows
  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void sendNotification_ValidCase_WithoutDelegate() {
    NotificationRequest.Webhook webhookBuilder = NotificationRequest.Webhook.newBuilder()
                                                     .setTemplateId(webhookTemplateName)
                                                     .addAllUrls(Collections.singletonList(webhookUrl))
                                                     .build();
    NotificationRequest notificationRequest =
        NotificationRequest.newBuilder().setId(id).setAccountId(accountId).setWebhook(webhookBuilder).build();
    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(List.of(true)).shouldRetry(false).build();
    when(notificationTemplateService.getTemplateAsString(eq(webhookTemplateName), any()))
        .thenReturn(Optional.of(TEST_NOTIFICATION_TEMPLATE_CONTENT));
    when(notificationSettingsService.checkIfWebhookIsSecret(any())).thenReturn(false);
    when(webhookSender.send(any(), any(), any(), any(), any())).thenReturn(notificationExpectedResponse);

    NotificationProcessingResponse notificationProcessingResponse = webhookService.send(notificationRequest);

    verify(webhookSender, times(1))
        .send(eq(List.of(webhookUrl)), eq(TEST_NOTIFICATION_TEMPLATE_CONTENT), anyString(), anyMap(), anyList());
    assertEquals(notificationExpectedResponse, notificationProcessingResponse);
  }

  @SneakyThrows
  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void sendNotification_ValidCase_WithDelegate() {
    NotificationRequest.Webhook webhookBuilder = NotificationRequest.Webhook.newBuilder()
                                                     .setTemplateId(webhookTemplateName)
                                                     .addAllUrls(Collections.singletonList(webhookUrl))
                                                     .build();
    NotificationRequest notificationRequest =
        NotificationRequest.newBuilder().setId(id).setAccountId(accountId).setWebhook(webhookBuilder).build();
    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(List.of(true)).shouldRetry(false).build();
    when(notificationTemplateService.getTemplateAsString(eq(webhookTemplateName), any()))
        .thenReturn(Optional.of(TEST_NOTIFICATION_TEMPLATE_CONTENT));
    when(notificationSettingsService.checkIfWebhookIsSecret(any())).thenReturn(true);
    when(delegateGrpcClientWrapper.submitAsyncTaskV2(any(), any())).thenReturn("");

    NotificationProcessingResponse notificationProcessingResponse = webhookService.send(notificationRequest);

    ArgumentCaptor<DelegateTaskRequest> delegateTaskRequestArgumentCaptor =
        ArgumentCaptor.forClass(DelegateTaskRequest.class);
    verify(delegateGrpcClientWrapper, times(1)).submitAsyncTaskV2(delegateTaskRequestArgumentCaptor.capture(), any());

    DelegateTaskRequest actualDelegateTaskRequest = delegateTaskRequestArgumentCaptor.getValue();
    WebhookTaskParams webhookTaskParams = (WebhookTaskParams) actualDelegateTaskRequest.getTaskParameters();

    assertThat(webhookTaskParams.getWebhookUrls()).isNotEmpty();
    assertThat(webhookTaskParams.getWebhookUrls()).hasSize(1);
    assertThat(webhookTaskParams.getWebhookUrls()).contains(webhookUrl);
    assertThat(webhookTaskParams.getMessage()).isEqualTo(TEST_NOTIFICATION_TEMPLATE_CONTENT);
    assertThat(webhookTaskParams.getHeaders()).isEmpty();
    assertEquals(notificationExpectedResponse, notificationProcessingResponse);
  }

  @SneakyThrows
  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void sendNotification_ValidCase() {
    NotificationRequest notificationRequest = NotificationRequest.newBuilder()
                                                  .setId(id)
                                                  .setAccountId(accountId)
                                                  .setWebhook(NotificationRequest.Webhook.newBuilder()
                                                                  .setTemplateId(webhookTemplateName)
                                                                  .addAllUrls(Collections.singletonList(webhookUrl))
                                                                  .build())
                                                  .build();
    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(Arrays.asList(true, false)).shouldRetry(false).build();
    when(notificationTemplateService.getTemplateAsString(eq(webhookTemplateName), any()))
        .thenReturn(Optional.empty(), Optional.of(TEST_NOTIFICATION_TEMPLATE_CONTENT));
    when(notificationSettingsService.getSendNotificationViaDelegate(eq(accountId))).thenReturn(false);
    when(webhookSender.send(any(), any(), any(), any(), any())).thenReturn(notificationExpectedResponse);

    NotificationProcessingResponse notificationProcessingResponse = webhookService.send(notificationRequest);
    assertTrue(notificationProcessingResponse.equals(NotificationProcessingResponse.trivialResponseWithNoRetries));

    notificationProcessingResponse = webhookService.send(notificationRequest);
    assertEquals(notificationExpectedResponse, notificationProcessingResponse);

    notificationRequest = NotificationRequest.newBuilder()
                              .setId(id)
                              .setAccountId(accountId)
                              .setWebhook(NotificationRequest.Webhook.newBuilder()
                                              .setTemplateId(webhookTemplateName)
                                              .addAllUrls(Collections.singletonList(webhookUrl))
                                              .build())
                              .build();
    notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(Arrays.asList(true, false)).shouldRetry(false).build();
    when(notificationTemplateService.getTemplateAsString(eq(webhookTemplateName), any()))
        .thenReturn(Optional.of("this is test notification"));
    when(notificationSettingsService.getSendNotificationViaDelegate(eq(accountId))).thenReturn(true);
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(NotificationTaskResponse.builder().processingResponse(notificationExpectedResponse).build());
    notificationProcessingResponse = webhookService.send(notificationRequest);
    assertEquals(notificationExpectedResponse, notificationProcessingResponse);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void sendSyncNotification_EmptyRequest() {
    NotificationRequest notificationRequest = NotificationRequest.newBuilder().build();

    assertThatThrownBy(() -> webhookService.sendSync(notificationRequest))
        .isInstanceOf(NotificationException.class)
        .hasMessage("Invalid webhook notification request");
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void sendSyncNotification_OnlyIdInRequest() {
    NotificationRequest notificationRequest = NotificationRequest.newBuilder().setId(id).build();

    assertThatThrownBy(() -> webhookService.sendSync(notificationRequest))
        .isInstanceOf(NotificationException.class)
        .hasMessage("Invalid webhook notification request");
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void sendSyncNotification_NoAccountIdInRequest() {
    NotificationRequest notificationRequest =
        NotificationRequest.newBuilder().setId(id).setWebhook(NotificationRequest.Webhook.newBuilder().build()).build();

    assertThatThrownBy(() -> webhookService.sendSync(notificationRequest))
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
                                                  .setWebhook(NotificationRequest.Webhook.newBuilder()
                                                                  .setTemplateId(webhookTemplateName)
                                                                  .addAllUrls(Collections.EMPTY_LIST)
                                                                  .build())
                                                  .build();

    assertThatThrownBy(() -> webhookService.sendSync(notificationRequest))
        .isInstanceOf(NotificationException.class)
        .hasMessageContaining("No webhookUrls found in notification request");
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void sendSyncNotification_NonExistingTemplateInRequest() {
    NotificationRequest notificationRequest = NotificationRequest.newBuilder()
                                                  .setId(id)
                                                  .setAccountId(accountId)
                                                  .setWebhook(NotificationRequest.Webhook.newBuilder()
                                                                  .setTemplateId(webhookTemplateName)
                                                                  .addAllUrls(Collections.singletonList(webhookUrl))
                                                                  .build())
                                                  .build();
    when(notificationTemplateService.getTemplateAsString(eq(webhookTemplateName), any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> webhookService.sendSync(notificationRequest))
        .isInstanceOf(NotificationException.class)
        .hasMessageContaining("Failed to send notification request")
        .hasMessageContaining("possibly due to no valid template with name");
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void sendSyncNotification_ValidCase_WithoutTemplateIdInRequest() {
    NotificationRequest.Webhook webhookBuilder = NotificationRequest.Webhook.newBuilder()
                                                     .addAllUrls(Collections.singletonList(webhookUrl))
                                                     .setMessage("custom-message")
                                                     .build();
    NotificationRequest notificationRequest =
        NotificationRequest.newBuilder().setId(id).setAccountId(accountId).setWebhook(webhookBuilder).build();

    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(Arrays.asList(true)).shouldRetry(false).build();
    when(notificationTemplateService.getTemplateAsString(eq(webhookTemplateName), any()))
        .thenReturn(Optional.of(TEST_NOTIFICATION_TEMPLATE_CONTENT));
    when(notificationSettingsService.checkIfWebhookIsSecret(any())).thenReturn(true);
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(NotificationTaskResponse.builder().processingResponse(notificationExpectedResponse).build());
    NotificationTaskResponse notificationTaskResponse = webhookService.sendSync(notificationRequest);

    verifyNoInteractions(notificationTemplateService);
    ArgumentCaptor<DelegateTaskRequest> delegateTaskRequestArgumentCaptor =
        ArgumentCaptor.forClass(DelegateTaskRequest.class);
    verify(delegateGrpcClientWrapper, times(1)).executeSyncTaskV2(delegateTaskRequestArgumentCaptor.capture());

    DelegateTaskRequest actualDelegateTaskRequest = delegateTaskRequestArgumentCaptor.getValue();
    WebhookTaskParams webhookTaskParams = (WebhookTaskParams) actualDelegateTaskRequest.getTaskParameters();

    assertThat(webhookTaskParams.getWebhookUrls()).isNotEmpty();
    assertThat(webhookTaskParams.getWebhookUrls()).hasSize(1);
    assertThat(webhookTaskParams.getWebhookUrls()).contains(webhookUrl);
    assertEquals(webhookBuilder.getMessage(), webhookTaskParams.getMessage());
    assertEquals(notificationExpectedResponse, notificationTaskResponse.getProcessingResponse());
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void sendSyncNotification_ValidCase_WithoutDelegate() {
    NotificationRequest.Webhook webhookBuilder = NotificationRequest.Webhook.newBuilder()
                                                     .addAllUrls(Collections.singletonList(webhookUrl))
                                                     .setTemplateId(webhookTemplateName)
                                                     .build();
    NotificationRequest notificationRequest =
        NotificationRequest.newBuilder().setId(id).setAccountId(accountId).setWebhook(webhookBuilder).build();

    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(Arrays.asList(true)).shouldRetry(false).build();
    when(notificationTemplateService.getTemplateAsString(eq(webhookTemplateName), any()))
        .thenReturn(Optional.of(TEST_NOTIFICATION_TEMPLATE_CONTENT));
    when(notificationSettingsService.checkIfWebhookIsSecret(any())).thenReturn(false);
    when(webhookSender.send(any(), any(), any(), any(), any())).thenReturn(notificationExpectedResponse);

    NotificationTaskResponse notificationTaskResponse = webhookService.sendSync(notificationRequest);

    verify(webhookSender, times(1))
        .send(anyList(), eq(TEST_NOTIFICATION_TEMPLATE_CONTENT), anyString(), anyMap(), anyList());

    assertEquals(notificationExpectedResponse, notificationTaskResponse.getProcessingResponse());
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void sendSyncNotification_ValidCase_UsingDelegate() {
    NotificationRequest.Webhook webhookBuilder = NotificationRequest.Webhook.newBuilder()
                                                     .addAllUrls(Collections.singletonList(webhookUrl))
                                                     .setTemplateId(webhookTemplateName)
                                                     .setMessage("custom-message")
                                                     .build();
    NotificationRequest notificationRequest =
        NotificationRequest.newBuilder().setId(id).setAccountId(accountId).setWebhook(webhookBuilder).build();

    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(Arrays.asList(true)).shouldRetry(false).build();
    when(notificationTemplateService.getTemplateAsString(eq(webhookTemplateName), any()))
        .thenReturn(Optional.of(TEST_NOTIFICATION_TEMPLATE_CONTENT));
    when(notificationSettingsService.checkIfWebhookIsSecret(any())).thenReturn(true);
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(NotificationTaskResponse.builder().processingResponse(notificationExpectedResponse).build());

    NotificationTaskResponse notificationTaskResponse = webhookService.sendSync(notificationRequest);

    ArgumentCaptor<DelegateTaskRequest> delegateTaskRequestArgumentCaptor =
        ArgumentCaptor.forClass(DelegateTaskRequest.class);
    verify(delegateGrpcClientWrapper, times(1)).executeSyncTaskV2(delegateTaskRequestArgumentCaptor.capture());

    DelegateTaskRequest actualDelegateTaskRequest = delegateTaskRequestArgumentCaptor.getValue();
    WebhookTaskParams webhookTaskParams = (WebhookTaskParams) actualDelegateTaskRequest.getTaskParameters();

    assertThat(webhookTaskParams.getWebhookUrls()).isNotEmpty();
    assertThat(webhookTaskParams.getWebhookUrls()).hasSize(1);
    assertThat(webhookTaskParams.getWebhookUrls()).contains(webhookUrl);
    assertEquals(TEST_NOTIFICATION_TEMPLATE_CONTENT, webhookTaskParams.getMessage());
    assertEquals(notificationExpectedResponse, notificationTaskResponse.getProcessingResponse());
  }
}

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
import io.harness.delegate.beans.MicrosoftTeamsTaskParams;
import io.harness.delegate.beans.NotificationProcessingResponse;
import io.harness.delegate.beans.NotificationTaskResponse;
import io.harness.notification.NotificationRequest;
import io.harness.notification.exception.NotificationException;
import io.harness.notification.remote.dto.MSTeamSettingDTO;
import io.harness.notification.remote.dto.NotificationSettingDTO;
import io.harness.notification.senders.MSTeamsSenderImpl;
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
public class MSTeamsServiceImplTest extends CategoryTest {
  private static final String TEST_NOTIFICATION_TEMPLATE_CONTENT = "This is a test notification";
  @Mock private NotificationSettingsService notificationSettingsService;
  @Mock private NotificationTemplateService notificationTemplateService;
  @Mock private MSTeamsSenderImpl msTeamsSender;
  @Mock private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Mock private NotificationSettingsHelper notificationSettingsHelper;
  private MSTeamsServiceImpl msTeamService;
  private String accountId = "accountId";
  private String msTeamsTemplateName = "msteams_test";
  private String msTeamsWebhookurl = "msteams-webhookurl";
  private String msTeamsWebhookurl2 = "msteams-webhookurl2";
  private String id = "id";

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    msTeamService = new MSTeamsServiceImpl(notificationSettingsService, notificationTemplateService, msTeamsSender,
        delegateGrpcClientWrapper, notificationSettingsHelper);
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void sendNotification_EmptyRequest() {
    NotificationRequest notificationRequest = NotificationRequest.newBuilder().build();
    NotificationProcessingResponse notificationProcessingResponse = msTeamService.send(notificationRequest);
    assertTrue(notificationProcessingResponse.equals(NotificationProcessingResponse.trivialResponseWithNoRetries));
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void sendNotification_OnlyIdInRequest() {
    NotificationRequest notificationRequest = NotificationRequest.newBuilder().setId(id).build();
    NotificationProcessingResponse notificationProcessingResponse = msTeamService.send(notificationRequest);
    assertTrue(notificationProcessingResponse.equals(NotificationProcessingResponse.trivialResponseWithNoRetries));
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void sendNotification_NoRecipientInRequest() {
    NotificationRequest notificationRequest = NotificationRequest.newBuilder()
                                                  .setId(id)
                                                  .setAccountId(accountId)
                                                  .setMsTeam(NotificationRequest.MSTeam.newBuilder()
                                                                 .setTemplateId(msTeamsTemplateName)
                                                                 .addAllMsTeamKeys(Collections.emptyList())
                                                                 .build())
                                                  .build();
    NotificationProcessingResponse notificationProcessingResponse = msTeamService.send(notificationRequest);
    assertTrue(notificationProcessingResponse.equals(NotificationProcessingResponse.trivialResponseWithNoRetries));
  }

  @SneakyThrows
  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void sendNotification_ValidCase_WithoutTemplateId_WithCustomContent() {
    NotificationRequest.MSTeam msTeamBuilder = NotificationRequest.MSTeam.newBuilder()
                                                   .addAllMsTeamKeys(Collections.singletonList(msTeamsWebhookurl))
                                                   .setMessage("some-message")
                                                   .build();
    NotificationRequest notificationRequest =
        NotificationRequest.newBuilder().setId(id).setAccountId(accountId).setMsTeam(msTeamBuilder).build();
    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(List.of(true)).shouldRetry(false).build();
    when(msTeamsSender.send(any(), any(), any(), any())).thenReturn(notificationExpectedResponse);

    NotificationProcessingResponse notificationProcessingResponse = msTeamService.send(notificationRequest);

    verifyNoInteractions(notificationTemplateService);
    verify(msTeamsSender, times(1))
        .send(eq(List.of(msTeamsWebhookurl)), eq(msTeamBuilder.getMessage()), anyString(), any());
    assertEquals(notificationExpectedResponse, notificationProcessingResponse);
  }

  @SneakyThrows
  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void sendNotification_ValidCase_WithoutDelegate() {
    NotificationRequest.MSTeam msTeamBuilder = NotificationRequest.MSTeam.newBuilder()
                                                   .setTemplateId(msTeamsTemplateName)
                                                   .addAllMsTeamKeys(Collections.singletonList(msTeamsWebhookurl))
                                                   .build();
    NotificationRequest notificationRequest =
        NotificationRequest.newBuilder().setId(id).setAccountId(accountId).setMsTeam(msTeamBuilder).build();
    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(List.of(true)).shouldRetry(false).build();
    when(notificationTemplateService.getTemplateAsString(eq(msTeamsTemplateName), any()))
        .thenReturn(Optional.of(TEST_NOTIFICATION_TEMPLATE_CONTENT));
    when(notificationSettingsService.checkIfWebhookIsSecret(any())).thenReturn(false);
    when(msTeamsSender.send(any(), any(), any(), any())).thenReturn(notificationExpectedResponse);

    NotificationProcessingResponse notificationProcessingResponse = msTeamService.send(notificationRequest);

    verify(msTeamsSender, times(1))
        .send(eq(List.of(msTeamsWebhookurl)), eq(TEST_NOTIFICATION_TEMPLATE_CONTENT), anyString(), any());
    assertEquals(notificationExpectedResponse, notificationProcessingResponse);
  }

  @SneakyThrows
  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void sendNotification_ValidCase_WithDelegate() {
    NotificationRequest.MSTeam msTeamBuilder = NotificationRequest.MSTeam.newBuilder()
                                                   .setTemplateId(msTeamsTemplateName)
                                                   .addAllMsTeamKeys(Collections.singletonList(msTeamsWebhookurl))
                                                   .build();
    NotificationRequest notificationRequest =
        NotificationRequest.newBuilder().setId(id).setAccountId(accountId).setMsTeam(msTeamBuilder).build();
    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(List.of(true)).shouldRetry(false).build();
    when(notificationTemplateService.getTemplateAsString(eq(msTeamsTemplateName), any()))
        .thenReturn(Optional.of(TEST_NOTIFICATION_TEMPLATE_CONTENT));
    when(notificationSettingsService.checkIfWebhookIsSecret(any())).thenReturn(true);
    when(delegateGrpcClientWrapper.submitAsyncTaskV2(any(), any())).thenReturn("");

    NotificationProcessingResponse notificationProcessingResponse = msTeamService.send(notificationRequest);

    ArgumentCaptor<DelegateTaskRequest> delegateTaskRequestArgumentCaptor =
        ArgumentCaptor.forClass(DelegateTaskRequest.class);
    verify(delegateGrpcClientWrapper, times(1)).submitAsyncTaskV2(delegateTaskRequestArgumentCaptor.capture(), any());

    DelegateTaskRequest actualDelegateTaskRequest = delegateTaskRequestArgumentCaptor.getValue();
    MicrosoftTeamsTaskParams microsoftTeamsTaskParams =
        (MicrosoftTeamsTaskParams) actualDelegateTaskRequest.getTaskParameters();

    assertThat(microsoftTeamsTaskParams.getMicrosoftTeamsWebhookUrls()).isNotEmpty();
    assertThat(microsoftTeamsTaskParams.getMicrosoftTeamsWebhookUrls()).hasSize(1);
    assertThat(microsoftTeamsTaskParams.getMicrosoftTeamsWebhookUrls()).contains(msTeamsWebhookurl);
    assertEquals(TEST_NOTIFICATION_TEMPLATE_CONTENT, microsoftTeamsTaskParams.getMessage());
    assertEquals(notificationExpectedResponse, notificationProcessingResponse);
  }

  @SneakyThrows
  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void sendNotification_ValidCaseMSteamUserGroup() {
    NotificationRequest notificationRequest =
        NotificationRequest.newBuilder()
            .setId(id)
            .setAccountId(accountId)
            .setMsTeam(NotificationRequest.MSTeam.newBuilder()
                           .setTemplateId(msTeamsTemplateName)
                           .addAllMsTeamKeys(Collections.singletonList(msTeamsWebhookurl))
                           .addUserGroup(NotificationRequest.UserGroup.newBuilder()
                                             .setIdentifier("identifier")
                                             .setProjectIdentifier("projectIdentifier")
                                             .setOrgIdentifier("orgIdentifier")
                                             .build())
                           .build())
            .build();
    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(Arrays.asList(true, false)).shouldRetry(false).build();
    when(notificationTemplateService.getTemplateAsString(eq(msTeamsTemplateName), any()))
        .thenReturn(Optional.empty(), Optional.of(TEST_NOTIFICATION_TEMPLATE_CONTENT));
    when(notificationSettingsService.getSendNotificationViaDelegate(eq(accountId))).thenReturn(false);
    when(msTeamsSender.send(any(), any(), any(), any())).thenReturn(notificationExpectedResponse);

    NotificationProcessingResponse notificationProcessingResponse = msTeamService.send(notificationRequest);
    assertEquals(notificationProcessingResponse, NotificationProcessingResponse.trivialResponseWithNoRetries);

    notificationProcessingResponse = msTeamService.send(notificationRequest);
    assertEquals(notificationExpectedResponse, notificationProcessingResponse);

    notificationRequest = NotificationRequest.newBuilder()
                              .setId(id)
                              .setAccountId(accountId)
                              .setMsTeam(NotificationRequest.MSTeam.newBuilder()
                                             .setTemplateId(msTeamsTemplateName)
                                             .addAllMsTeamKeys(Collections.singletonList(msTeamsWebhookurl))
                                             .addUserGroup(NotificationRequest.UserGroup.newBuilder()
                                                               .setIdentifier("identifier")
                                                               .setProjectIdentifier("projectIdentifier")
                                                               .setOrgIdentifier("orgIdentifier")
                                                               .build())
                                             .build())
                              .build();
    notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(Arrays.asList(true, false)).shouldRetry(false).build();
    when(notificationTemplateService.getTemplateAsString(eq(msTeamsTemplateName), any()))
        .thenReturn(Optional.of("this is test notification"));
    when(notificationSettingsService.getSendNotificationViaDelegate(eq(accountId))).thenReturn(true);
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(NotificationTaskResponse.builder().processingResponse(notificationExpectedResponse).build());
    notificationProcessingResponse = msTeamService.send(notificationRequest);
    assertEquals(notificationExpectedResponse, notificationProcessingResponse);
  }

  @SneakyThrows
  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void sendNotification_WhenSecretIsPassedForWebhookURl() {
    NotificationRequest notificationRequest =
        NotificationRequest.newBuilder()
            .setId(id)
            .setAccountId(accountId)
            .setMsTeam(NotificationRequest.MSTeam.newBuilder()
                           .setTemplateId(msTeamsTemplateName)
                           .addAllMsTeamKeys(Collections.singletonList(msTeamsWebhookurl))
                           .setExpressionFunctorToken(HashGenerator.generateIntegerHash())
                           .setOrgIdentifier("orgIdentifier")
                           .setProjectIdentifier("projectIdentifier")
                           .build())
            .build();
    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(Arrays.asList(true, false)).shouldRetry(false).build();
    when(notificationTemplateService.getTemplateAsString(eq(msTeamsTemplateName), any()))
        .thenReturn(Optional.empty(), Optional.of(TEST_NOTIFICATION_TEMPLATE_CONTENT));
    when(notificationSettingsService.getSendNotificationViaDelegate(eq(accountId))).thenReturn(false);
    when(msTeamsSender.send(any(), any(), any(), any())).thenReturn(notificationExpectedResponse);

    NotificationProcessingResponse notificationProcessingResponse = msTeamService.send(notificationRequest);
    assertEquals(notificationProcessingResponse, NotificationProcessingResponse.trivialResponseWithNoRetries);

    notificationProcessingResponse = msTeamService.send(notificationRequest);
    assertEquals(notificationExpectedResponse, notificationProcessingResponse);

    notificationRequest = NotificationRequest.newBuilder()
                              .setId(id)
                              .setAccountId(accountId)
                              .setMsTeam(NotificationRequest.MSTeam.newBuilder()
                                             .setTemplateId(msTeamsTemplateName)
                                             .addAllMsTeamKeys(Arrays.asList(msTeamsWebhookurl, msTeamsWebhookurl2))
                                             .setExpressionFunctorToken(HashGenerator.generateIntegerHash())
                                             .setOrgIdentifier("orgIdentifier")
                                             .setProjectIdentifier("projectIdentifier")
                                             .build())
                              .build();
    notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(Arrays.asList(true, false)).shouldRetry(false).build();
    when(notificationTemplateService.getTemplateAsString(eq(msTeamsTemplateName), any()))
        .thenReturn(Optional.of("this is test notification"));
    when(notificationSettingsService.getSendNotificationViaDelegate(eq(accountId))).thenReturn(true);
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(NotificationTaskResponse.builder().processingResponse(notificationExpectedResponse).build());
    notificationProcessingResponse = msTeamService.send(notificationRequest);
    assertEquals(notificationExpectedResponse, notificationProcessingResponse);
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void sendTestNotification_CheckAllGaurds() {
    final NotificationSettingDTO notificationSettingDTO1 = MSTeamSettingDTO.builder().build();
    assertThatThrownBy(() -> msTeamService.sendTestNotification(notificationSettingDTO1))
        .isInstanceOf(NotificationException.class);

    final NotificationSettingDTO notificationSettingDTO2 = MSTeamSettingDTO.builder().accountId(accountId).build();
    assertThatThrownBy(() -> msTeamService.sendTestNotification(notificationSettingDTO2))
        .isInstanceOf(NotificationException.class);

    final NotificationSettingDTO notificationSettingDTO3 =
        MSTeamSettingDTO.builder().recipient("email@harness.io").build();
    assertThatThrownBy(() -> msTeamService.sendTestNotification(notificationSettingDTO3))
        .isInstanceOf(NotificationException.class);
  }

  @SneakyThrows
  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void sendTestNotification_ValidRequest() {
    final NotificationSettingDTO notificationSettingDTO4 =
        MSTeamSettingDTO.builder().accountId(accountId).recipient(msTeamsWebhookurl).build();
    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(List.of(true)).shouldRetry(false).build();
    when(msTeamsSender.send(any(), any(), any(), any())).thenReturn(notificationExpectedResponse);
    when(notificationTemplateService.getTemplateAsString(any(), any()))
        .thenReturn(Optional.of(TEST_NOTIFICATION_TEMPLATE_CONTENT));
    boolean response = msTeamService.sendTestNotification(notificationSettingDTO4);
    assertTrue(response);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void sendSyncNotification_EmptyRequest() {
    NotificationRequest notificationRequest = NotificationRequest.newBuilder().build();

    assertThatThrownBy(() -> msTeamService.sendSync(notificationRequest))
        .isInstanceOf(NotificationException.class)
        .hasMessage("Invalid microsoft teams notification request");
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void sendSyncNotification_OnlyIdInRequest() {
    NotificationRequest notificationRequest = NotificationRequest.newBuilder().setId(id).build();

    assertThatThrownBy(() -> msTeamService.sendSync(notificationRequest))
        .isInstanceOf(NotificationException.class)
        .hasMessage("Invalid microsoft teams notification request");
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void sendSyncNotification_NoAccountIdInRequest() {
    NotificationRequest notificationRequest =
        NotificationRequest.newBuilder().setId(id).setMsTeam(NotificationRequest.MSTeam.newBuilder().build()).build();

    assertThatThrownBy(() -> msTeamService.sendSync(notificationRequest))
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
                                                  .setMsTeam(NotificationRequest.MSTeam.newBuilder()
                                                                 .setTemplateId(msTeamsTemplateName)
                                                                 .addAllMsTeamKeys(Collections.emptyList())
                                                                 .build())
                                                  .build();

    assertThatThrownBy(() -> msTeamService.sendSync(notificationRequest))
        .isInstanceOf(NotificationException.class)
        .hasMessageContaining("No microsoft teams webhook found in notification request");
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void sendSyncNotification_NonExistingTemplateInRequest() {
    NotificationRequest notificationRequest = NotificationRequest.newBuilder()
                                                  .setId(id)
                                                  .setAccountId(accountId)
                                                  .setMsTeam(NotificationRequest.MSTeam.newBuilder()
                                                                 .setTemplateId(msTeamsTemplateName)
                                                                 .addAllMsTeamKeys(List.of(msTeamsWebhookurl))
                                                                 .build())
                                                  .build();
    when(notificationTemplateService.getTemplateAsString(eq(msTeamsTemplateName), any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> msTeamService.sendSync(notificationRequest))
        .isInstanceOf(NotificationException.class)
        .hasMessageContaining("Failed to send notification request")
        .hasMessageContaining("possibly due to no valid template with name");
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void sendSyncNotification_ValidCase_WithoutTemplateIdInRequest() {
    NotificationRequest.MSTeam msTeamBuilder = NotificationRequest.MSTeam.newBuilder()
                                                   .addAllMsTeamKeys(Collections.singletonList(msTeamsWebhookurl))
                                                   .setMessage("custom-message")
                                                   .build();
    NotificationRequest notificationRequest =
        NotificationRequest.newBuilder().setId(id).setAccountId(accountId).setMsTeam(msTeamBuilder).build();

    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(List.of(true)).shouldRetry(false).build();
    when(notificationTemplateService.getTemplateAsString(eq(msTeamsTemplateName), any()))
        .thenReturn(Optional.of(TEST_NOTIFICATION_TEMPLATE_CONTENT));
    when(notificationSettingsService.checkIfWebhookIsSecret(any())).thenReturn(true);
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(NotificationTaskResponse.builder().processingResponse(notificationExpectedResponse).build());
    NotificationTaskResponse notificationTaskResponse = msTeamService.sendSync(notificationRequest);

    verifyNoInteractions(notificationTemplateService);
    ArgumentCaptor<DelegateTaskRequest> delegateTaskRequestArgumentCaptor =
        ArgumentCaptor.forClass(DelegateTaskRequest.class);
    verify(delegateGrpcClientWrapper, times(1)).executeSyncTaskV2(delegateTaskRequestArgumentCaptor.capture());

    DelegateTaskRequest actualDelegateTaskRequest = delegateTaskRequestArgumentCaptor.getValue();
    MicrosoftTeamsTaskParams mailTaskParams = (MicrosoftTeamsTaskParams) actualDelegateTaskRequest.getTaskParameters();

    assertThat(mailTaskParams.getMicrosoftTeamsWebhookUrls()).isNotEmpty();
    assertThat(mailTaskParams.getMicrosoftTeamsWebhookUrls()).hasSize(1);
    assertThat(mailTaskParams.getMicrosoftTeamsWebhookUrls()).contains(msTeamsWebhookurl);
    assertEquals(msTeamBuilder.getMessage(), mailTaskParams.getMessage());
    assertEquals(notificationExpectedResponse, notificationTaskResponse.getProcessingResponse());
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void sendSyncNotification_ValidCase_WithoutDelegate() {
    NotificationRequest.MSTeam msTeamBuilder = NotificationRequest.MSTeam.newBuilder()
                                                   .setTemplateId(msTeamsTemplateName)
                                                   .addAllMsTeamKeys(Collections.singletonList(msTeamsWebhookurl))
                                                   .build();
    NotificationRequest notificationRequest =
        NotificationRequest.newBuilder().setId(id).setAccountId(accountId).setMsTeam(msTeamBuilder).build();
    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(List.of(true)).shouldRetry(false).build();
    when(notificationTemplateService.getTemplateAsString(eq(msTeamsTemplateName), any()))
        .thenReturn(Optional.of(TEST_NOTIFICATION_TEMPLATE_CONTENT));
    when(notificationSettingsService.checkIfWebhookIsSecret(any())).thenReturn(false);
    when(msTeamsSender.send(any(), any(), any(), any())).thenReturn(notificationExpectedResponse);

    NotificationTaskResponse notificationTaskResponse = msTeamService.sendSync(notificationRequest);

    verify(msTeamsSender, times(1)).send(anyList(), eq(TEST_NOTIFICATION_TEMPLATE_CONTENT), anyString(), anyList());

    assertEquals(notificationExpectedResponse, notificationTaskResponse.getProcessingResponse());
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void sendSyncNotification_ValidCase_UsingDelegate() {
    NotificationRequest.MSTeam msTeamBuilder = NotificationRequest.MSTeam.newBuilder()
                                                   .setTemplateId(msTeamsTemplateName)
                                                   .addAllMsTeamKeys(Collections.singletonList(msTeamsWebhookurl))
                                                   .setMessage("custom-message")
                                                   .build();
    NotificationRequest notificationRequest =
        NotificationRequest.newBuilder().setId(id).setAccountId(accountId).setMsTeam(msTeamBuilder).build();
    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(List.of(true)).shouldRetry(false).build();
    when(notificationTemplateService.getTemplateAsString(eq(msTeamsTemplateName), any()))
        .thenReturn(Optional.of(TEST_NOTIFICATION_TEMPLATE_CONTENT));
    when(notificationSettingsService.checkIfWebhookIsSecret(any())).thenReturn(true);
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(NotificationTaskResponse.builder().processingResponse(notificationExpectedResponse).build());

    NotificationTaskResponse notificationTaskResponse = msTeamService.sendSync(notificationRequest);

    ArgumentCaptor<DelegateTaskRequest> delegateTaskRequestArgumentCaptor =
        ArgumentCaptor.forClass(DelegateTaskRequest.class);
    verify(delegateGrpcClientWrapper, times(1)).executeSyncTaskV2(delegateTaskRequestArgumentCaptor.capture());

    DelegateTaskRequest actualDelegateTaskRequest = delegateTaskRequestArgumentCaptor.getValue();
    MicrosoftTeamsTaskParams mailTaskParams = (MicrosoftTeamsTaskParams) actualDelegateTaskRequest.getTaskParameters();
    assertThat(mailTaskParams.getMicrosoftTeamsWebhookUrls()).isNotEmpty();
    assertThat(mailTaskParams.getMicrosoftTeamsWebhookUrls()).hasSize(1);
    assertThat(mailTaskParams.getMicrosoftTeamsWebhookUrls()).contains(msTeamsWebhookurl);
    assertEquals(TEST_NOTIFICATION_TEMPLATE_CONTENT, mailTaskParams.getMessage());
    assertEquals(notificationExpectedResponse, notificationTaskResponse.getProcessingResponse());
  }
}

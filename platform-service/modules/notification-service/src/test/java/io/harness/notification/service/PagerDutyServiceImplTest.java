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
import io.harness.delegate.beans.PagerDutyTaskParams;
import io.harness.notification.NotificationRequest;
import io.harness.notification.exception.NotificationException;
import io.harness.notification.remote.dto.NotificationSettingDTO;
import io.harness.notification.remote.dto.PagerDutySettingDTO;
import io.harness.notification.senders.PagerDutySenderImpl;
import io.harness.notification.service.PagerDutyServiceImpl.PagerDutyTemplate;
import io.harness.notification.service.api.NotificationSettingsService;
import io.harness.notification.service.api.NotificationTemplateService;
import io.harness.notification.utils.NotificationSettingsHelper;
import io.harness.rule.Owner;
import io.harness.serializer.YamlUtils;
import io.harness.service.DelegateGrpcClientWrapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.dikhan.pagerduty.client.events.domain.LinkContext;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.SneakyThrows;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PL)
public class PagerDutyServiceImplTest extends CategoryTest {
  private static final String TEST_NOTIFICATION_TEMPLATE_CONTENT = "This is a test notification";
  @Mock private NotificationSettingsService notificationSettingsService;
  @Mock private NotificationTemplateService notificationTemplateService;
  @Mock private YamlUtils yamlUtils;
  @Mock private PagerDutySenderImpl pagerDutySender;
  @Mock private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Mock private NotificationSettingsHelper notificationSettingsHelper;
  private PagerDutyServiceImpl pagerdutyService;
  private String accountId = "accountId";
  private String pdTemplateName = "pd_test";
  private String pdKey = "pd-key";
  private String pdKey2 = "pd-key2";
  private String id = "id";
  private PagerDutyTemplate pdTemplate = new PagerDutyTemplate();

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    pagerdutyService = new PagerDutyServiceImpl(notificationSettingsService, notificationTemplateService, yamlUtils,
        pagerDutySender, delegateGrpcClientWrapper, notificationSettingsHelper);
    pdTemplate.setSummary("this is test mail");
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void sendNotification_EmptyRequest() {
    NotificationRequest notificationRequest = NotificationRequest.newBuilder().build();
    NotificationProcessingResponse notificationProcessingResponse = pagerdutyService.send(notificationRequest);
    assertTrue(notificationProcessingResponse.equals(NotificationProcessingResponse.trivialResponseWithNoRetries));
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void sendNotification_OnlyIdInRequest() {
    NotificationRequest notificationRequest = NotificationRequest.newBuilder().setId(id).build();
    NotificationProcessingResponse notificationProcessingResponse = pagerdutyService.send(notificationRequest);
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
            .setPagerDuty(NotificationRequest.PagerDuty.newBuilder()
                              .addAllPagerDutyIntegrationKeys(Collections.singletonList(pdKey))
                              .build())
            .build();
    NotificationProcessingResponse notificationProcessingResponse = pagerdutyService.send(notificationRequest);
    assertTrue(notificationProcessingResponse.equals(NotificationProcessingResponse.trivialResponseWithNoRetries));
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void sendNotification_NoRecipientInRequest() {
    NotificationRequest notificationRequest =
        NotificationRequest.newBuilder()
            .setId(id)
            .setAccountId(accountId)
            .setPagerDuty(NotificationRequest.PagerDuty.newBuilder()
                              .setTemplateId(pdTemplateName)
                              .addAllPagerDutyIntegrationKeys(Collections.EMPTY_LIST)
                              .build())
            .build();
    NotificationProcessingResponse notificationProcessingResponse = pagerdutyService.send(notificationRequest);
    assertTrue(notificationProcessingResponse.equals(NotificationProcessingResponse.trivialResponseWithNoRetries));
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
            .setPagerDuty(NotificationRequest.PagerDuty.newBuilder()
                              .setTemplateId(pdTemplateName)
                              .addAllPagerDutyIntegrationKeys(Collections.singletonList(pdKey))
                              .build())
            .build();
    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(Arrays.asList(true, false)).shouldRetry(false).build();
    when(notificationTemplateService.getTemplateAsString(eq(pdTemplateName), any()))
        .thenReturn(Optional.empty(), Optional.of("This is a test notification"));
    when(notificationSettingsService.getSendNotificationViaDelegate(eq(accountId))).thenReturn(false);
    when(pagerDutySender.send(any(), any(), any(), any(), any())).thenReturn(notificationExpectedResponse);
    when(yamlUtils.read(any(), (TypeReference<PagerDutyTemplate>) any())).thenReturn(pdTemplate);

    NotificationProcessingResponse notificationProcessingResponse = pagerdutyService.send(notificationRequest);
    assertTrue(notificationProcessingResponse.equals(NotificationProcessingResponse.trivialResponseWithNoRetries));

    notificationProcessingResponse = pagerdutyService.send(notificationRequest);
    assertEquals(notificationExpectedResponse, notificationProcessingResponse);

    notificationRequest = NotificationRequest.newBuilder()
                              .setId(id)
                              .setAccountId(accountId)
                              .setPagerDuty(NotificationRequest.PagerDuty.newBuilder()
                                                .setTemplateId(pdTemplateName)
                                                .addAllPagerDutyIntegrationKeys(Collections.singletonList(pdKey))
                                                .build())
                              .build();
    notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(Arrays.asList(true, false)).shouldRetry(false).build();
    when(notificationTemplateService.getTemplateAsString(eq(pdTemplateName), any()))
        .thenReturn(Optional.of("this is test notification"));
    when(notificationSettingsService.getSendNotificationViaDelegate(eq(accountId))).thenReturn(true);
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(NotificationTaskResponse.builder().processingResponse(notificationExpectedResponse).build());
    notificationProcessingResponse = pagerdutyService.send(notificationRequest);
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
            .setPagerDuty(NotificationRequest.PagerDuty.newBuilder()
                              .setTemplateId(pdTemplateName)
                              .addAllPagerDutyIntegrationKeys(Collections.singletonList(pdKey))
                              .setExpressionFunctorToken(HashGenerator.generateIntegerHash())
                              .setOrgIdentifier("orgIdentifier")
                              .setProjectIdentifier("projectIdentifier")
                              .build())
            .build();
    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(Arrays.asList(true, false)).shouldRetry(false).build();
    when(notificationTemplateService.getTemplateAsString(eq(pdTemplateName), any()))
        .thenReturn(Optional.empty(), Optional.of("This is a test notification"));
    when(notificationSettingsService.getSendNotificationViaDelegate(eq(accountId))).thenReturn(false);
    when(pagerDutySender.send(any(), any(), any(), any(), any())).thenReturn(notificationExpectedResponse);
    when(yamlUtils.read(any(), (TypeReference<PagerDutyTemplate>) any())).thenReturn(pdTemplate);

    NotificationProcessingResponse notificationProcessingResponse = pagerdutyService.send(notificationRequest);
    assertTrue(notificationProcessingResponse.equals(NotificationProcessingResponse.trivialResponseWithNoRetries));

    notificationProcessingResponse = pagerdutyService.send(notificationRequest);
    assertEquals(notificationExpectedResponse, notificationProcessingResponse);

    notificationRequest = NotificationRequest.newBuilder()
                              .setId(id)
                              .setAccountId(accountId)
                              .setPagerDuty(NotificationRequest.PagerDuty.newBuilder()
                                                .setTemplateId(pdTemplateName)
                                                .addAllPagerDutyIntegrationKeys(Arrays.asList(pdKey, pdKey2))
                                                .setExpressionFunctorToken(HashGenerator.generateIntegerHash())
                                                .setOrgIdentifier("orgIdentifier")
                                                .setProjectIdentifier("projectIdentifier")
                                                .build())
                              .build();
    notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(Arrays.asList(true, false)).shouldRetry(false).build();
    when(notificationTemplateService.getTemplateAsString(eq(pdTemplateName), any()))
        .thenReturn(Optional.of("this is test notification"));
    when(notificationSettingsService.getSendNotificationViaDelegate(eq(accountId))).thenReturn(true);
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(NotificationTaskResponse.builder().processingResponse(notificationExpectedResponse).build());
    notificationProcessingResponse = pagerdutyService.send(notificationRequest);
    assertEquals(notificationExpectedResponse, notificationProcessingResponse);
  }

  @SneakyThrows
  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void sendNotification_ValidCasePagerDutyUserGroup() {
    NotificationRequest notificationRequest =
        NotificationRequest.newBuilder()
            .setId(id)
            .setAccountId(accountId)
            .setPagerDuty(NotificationRequest.PagerDuty.newBuilder()
                              .setTemplateId(pdTemplateName)
                              .addAllPagerDutyIntegrationKeys(Collections.singletonList(pdKey))
                              .addUserGroup(NotificationRequest.UserGroup.newBuilder()
                                                .setIdentifier("identifier")
                                                .setProjectIdentifier("projectIdentifier")
                                                .setOrgIdentifier("orgIdentifier")
                                                .build())
                              .build())
            .build();
    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(Arrays.asList(true, false)).shouldRetry(false).build();
    when(notificationTemplateService.getTemplateAsString(eq(pdTemplateName), any()))
        .thenReturn(Optional.empty(), Optional.of("This is a test notification"));
    when(notificationSettingsService.getSendNotificationViaDelegate(eq(accountId))).thenReturn(false);
    when(pagerDutySender.send(any(), any(), any(), any(), any())).thenReturn(notificationExpectedResponse);
    when(yamlUtils.read(any(), (TypeReference<PagerDutyTemplate>) any())).thenReturn(pdTemplate);

    NotificationProcessingResponse notificationProcessingResponse = pagerdutyService.send(notificationRequest);
    assertEquals(notificationProcessingResponse, NotificationProcessingResponse.trivialResponseWithNoRetries);

    notificationProcessingResponse = pagerdutyService.send(notificationRequest);
    assertEquals(notificationExpectedResponse, notificationProcessingResponse);

    notificationRequest = NotificationRequest.newBuilder()
                              .setId(id)
                              .setAccountId(accountId)
                              .setPagerDuty(NotificationRequest.PagerDuty.newBuilder()
                                                .setTemplateId(pdTemplateName)
                                                .addAllPagerDutyIntegrationKeys(Collections.singletonList(pdKey))
                                                .addUserGroup(NotificationRequest.UserGroup.newBuilder()
                                                                  .setIdentifier("identifier")
                                                                  .setProjectIdentifier("projectIdentifier")
                                                                  .setOrgIdentifier("orgIdentifier")
                                                                  .build())
                                                .build())
                              .build();
    notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(Arrays.asList(true, false)).shouldRetry(false).build();
    when(notificationTemplateService.getTemplateAsString(eq(pdTemplateName), any()))
        .thenReturn(Optional.of("this is test notification"));
    when(notificationSettingsService.getSendNotificationViaDelegate(eq(accountId))).thenReturn(true);
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(NotificationTaskResponse.builder().processingResponse(notificationExpectedResponse).build());
    notificationProcessingResponse = pagerdutyService.send(notificationRequest);
    assertEquals(notificationExpectedResponse, notificationProcessingResponse);
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void sendTestNotification_CheckAllGaurds() {
    final NotificationSettingDTO notificationSettingDTO1 = PagerDutySettingDTO.builder().build();
    assertThatThrownBy(() -> pagerdutyService.sendTestNotification(notificationSettingDTO1))
        .isInstanceOf(NotificationException.class);

    final NotificationSettingDTO notificationSettingDTO2 = PagerDutySettingDTO.builder().accountId(accountId).build();
    assertThatThrownBy(() -> pagerdutyService.sendTestNotification(notificationSettingDTO2))
        .isInstanceOf(NotificationException.class);

    final NotificationSettingDTO notificationSettingDTO3 =
        PagerDutySettingDTO.builder().recipient("email@harness.io").build();
    assertThatThrownBy(() -> pagerdutyService.sendTestNotification(notificationSettingDTO3))
        .isInstanceOf(NotificationException.class);
  }

  @SneakyThrows
  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void sendTestNotification_ValidRequest() {
    final NotificationSettingDTO notificationSettingDTO4 =
        PagerDutySettingDTO.builder().accountId(accountId).recipient("email@harness.io").build();
    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(Arrays.asList(true)).shouldRetry(false).build();
    when(pagerDutySender.send(any(), any(), any(), any(), any())).thenReturn(notificationExpectedResponse);
    when(notificationTemplateService.getTemplateAsString(any(), any()))
        .thenReturn(Optional.of("This is a test notification"));
    when(yamlUtils.read(any(), (TypeReference<PagerDutyTemplate>) any())).thenReturn(pdTemplate);
    boolean response = pagerdutyService.sendTestNotification(notificationSettingDTO4);
    assertTrue(response);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void sendSyncNotification_EmptyRequest() {
    NotificationRequest notificationRequest = NotificationRequest.newBuilder().build();

    assertThatThrownBy(() -> pagerdutyService.sendSync(notificationRequest))
        .isInstanceOf(NotificationException.class)
        .hasMessage("Invalid pager duty notification request");
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void sendSyncNotification_OnlyIdInRequest() {
    NotificationRequest notificationRequest = NotificationRequest.newBuilder().setId(id).build();

    assertThatThrownBy(() -> pagerdutyService.sendSync(notificationRequest))
        .isInstanceOf(NotificationException.class)
        .hasMessage("Invalid pager duty notification request");
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void sendSyncNotification_NoAccountIdInRequest() {
    NotificationRequest notificationRequest = NotificationRequest.newBuilder()
                                                  .setId(id)
                                                  .setPagerDuty(NotificationRequest.PagerDuty.newBuilder().build())
                                                  .build();

    assertThatThrownBy(() -> pagerdutyService.sendSync(notificationRequest))
        .isInstanceOf(NotificationException.class)
        .hasMessageContaining("No account id encountered for");
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void sendSyncNotification_NoRecipientInRequest() {
    NotificationRequest notificationRequest =
        NotificationRequest.newBuilder()
            .setId(id)
            .setAccountId(accountId)
            .setPagerDuty(NotificationRequest.PagerDuty.newBuilder()
                              .setTemplateId(pdTemplateName)
                              .addAllPagerDutyIntegrationKeys(Collections.EMPTY_LIST)
                              .build())
            .build();

    assertThatThrownBy(() -> pagerdutyService.sendSync(notificationRequest))
        .isInstanceOf(NotificationException.class)
        .hasMessageContaining("No pagerduty integration key found in notification request");
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void sendSyncNotification_NonExistingTemplateInRequest() {
    NotificationRequest notificationRequest =
        NotificationRequest.newBuilder()
            .setId(id)
            .setAccountId(accountId)
            .setPagerDuty(NotificationRequest.PagerDuty.newBuilder()
                              .setTemplateId(pdTemplateName)
                              .addAllPagerDutyIntegrationKeys(Collections.singletonList(pdKey))
                              .build())
            .build();
    when(notificationTemplateService.getTemplateAsString(eq(pdTemplateName), any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> pagerdutyService.sendSync(notificationRequest))
        .isInstanceOf(NotificationException.class)
        .hasMessageContaining("Failed to send notification request")
        .hasMessageContaining("possibly due to no valid template with name");
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void sendSyncNotification_ValidCase_WithoutTemplateIdInRequest() {
    NotificationRequest.PagerDuty pdBuilder = NotificationRequest.PagerDuty.newBuilder()
                                                  .addAllPagerDutyIntegrationKeys(Collections.singletonList(pdKey))
                                                  .setSummary("custom-message")
                                                  .putAllLinks(Map.of("sample-link-href", "sample-link-text"))
                                                  .build();
    NotificationRequest notificationRequest =
        NotificationRequest.newBuilder().setId(id).setAccountId(accountId).setPagerDuty(pdBuilder).build();

    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(List.of(true)).shouldRetry(false).build();
    when(notificationTemplateService.getTemplateAsString(eq(pdTemplateName), any()))
        .thenReturn(Optional.of(TEST_NOTIFICATION_TEMPLATE_CONTENT));
    when(notificationSettingsService.checkIfWebhookIsSecret(any())).thenReturn(true);
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(NotificationTaskResponse.builder().processingResponse(notificationExpectedResponse).build());
    NotificationTaskResponse notificationTaskResponse = pagerdutyService.sendSync(notificationRequest);

    verifyNoInteractions(notificationTemplateService);
    ArgumentCaptor<DelegateTaskRequest> delegateTaskRequestArgumentCaptor =
        ArgumentCaptor.forClass(DelegateTaskRequest.class);
    verify(delegateGrpcClientWrapper, times(1)).executeSyncTaskV2(delegateTaskRequestArgumentCaptor.capture());

    DelegateTaskRequest actualDelegateTaskRequest = delegateTaskRequestArgumentCaptor.getValue();
    PagerDutyTaskParams pagerDutyTaskParams = (PagerDutyTaskParams) actualDelegateTaskRequest.getTaskParameters();

    assertThat(pagerDutyTaskParams.getPagerDutyKeys()).isNotEmpty();
    assertThat(pagerDutyTaskParams.getPagerDutyKeys()).hasSize(1);
    assertThat(pagerDutyTaskParams.getPagerDutyKeys()).contains(pdKey);
    assertEquals(pdBuilder.getSummary(), pagerDutyTaskParams.getPayload().getSummary());
    LinkContext linkContext = pagerDutyTaskParams.getLinks().get(0);
    assertThat(pdBuilder.getLinksMap()).containsEntry(linkContext.getHref(), linkContext.getText());
    assertEquals(notificationExpectedResponse, notificationTaskResponse.getProcessingResponse());
  }

  @SneakyThrows
  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void sendSyncNotification_ValidCase_WithoutDelegate() {
    NotificationRequest.PagerDuty pdBuilder = NotificationRequest.PagerDuty.newBuilder()
                                                  .setTemplateId(pdTemplateName)
                                                  .addAllPagerDutyIntegrationKeys(Collections.singletonList(pdKey))
                                                  .build();
    NotificationRequest notificationRequest =
        NotificationRequest.newBuilder().setId(id).setAccountId(accountId).setPagerDuty(pdBuilder).build();

    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(Arrays.asList(true)).shouldRetry(false).build();
    when(notificationTemplateService.getTemplateAsString(eq(pdTemplateName), any()))
        .thenReturn(Optional.of(TEST_NOTIFICATION_TEMPLATE_CONTENT));
    when(yamlUtils.read(any(), (TypeReference<PagerDutyTemplate>) any())).thenReturn(pdTemplate);
    when(notificationSettingsService.checkIfWebhookIsSecret(any())).thenReturn(false);
    when(pagerDutySender.send(any(), any(), any(), any(), any())).thenReturn(notificationExpectedResponse);

    NotificationTaskResponse notificationTaskResponse = pagerdutyService.sendSync(notificationRequest);

    verify(pagerDutySender, times(1)).send(anyList(), any(), any(), anyString(), anyList());

    assertEquals(notificationExpectedResponse, notificationTaskResponse.getProcessingResponse());
  }

  @SneakyThrows
  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void sendSyncNotification_ValidCase_UsingDelegate() {
    NotificationRequest.PagerDuty pdBuilder = NotificationRequest.PagerDuty.newBuilder()
                                                  .setTemplateId(pdTemplateName)
                                                  .addAllPagerDutyIntegrationKeys(Collections.singletonList(pdKey))
                                                  .setSummary("custom-message")
                                                  .putAllLinks(Map.of("sample-link-href", "sample-link-text"))
                                                  .build();
    NotificationRequest notificationRequest =
        NotificationRequest.newBuilder().setId(id).setAccountId(accountId).setPagerDuty(pdBuilder).build();

    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(Arrays.asList(true)).shouldRetry(false).build();
    when(notificationTemplateService.getTemplateAsString(eq(pdTemplateName), any()))
        .thenReturn(Optional.of(TEST_NOTIFICATION_TEMPLATE_CONTENT));
    when(yamlUtils.read(any(), (TypeReference<PagerDutyTemplate>) any())).thenReturn(pdTemplate);
    when(notificationSettingsService.checkIfWebhookIsSecret(any())).thenReturn(true);
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(NotificationTaskResponse.builder().processingResponse(notificationExpectedResponse).build());

    NotificationTaskResponse notificationTaskResponse = pagerdutyService.sendSync(notificationRequest);

    ArgumentCaptor<DelegateTaskRequest> delegateTaskRequestArgumentCaptor =
        ArgumentCaptor.forClass(DelegateTaskRequest.class);
    verify(delegateGrpcClientWrapper, times(1)).executeSyncTaskV2(delegateTaskRequestArgumentCaptor.capture());

    DelegateTaskRequest actualDelegateTaskRequest = delegateTaskRequestArgumentCaptor.getValue();
    PagerDutyTaskParams pagerDutyTaskParams = (PagerDutyTaskParams) actualDelegateTaskRequest.getTaskParameters();

    assertThat(pagerDutyTaskParams.getPagerDutyKeys()).isNotEmpty();
    assertThat(pagerDutyTaskParams.getPagerDutyKeys()).hasSize(1);
    assertThat(pagerDutyTaskParams.getPagerDutyKeys()).contains(pdKey);
    assertEquals(pdTemplate.getSummary(), pagerDutyTaskParams.getPayload().getSummary());
    assertThat(pagerDutyTaskParams.getLinks()).isEmpty();
    assertEquals(notificationExpectedResponse, notificationTaskResponse.getProcessingResponse());
  }
}

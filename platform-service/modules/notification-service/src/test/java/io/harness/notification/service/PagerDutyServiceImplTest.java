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
import io.harness.notification.remote.dto.PagerDutySettingDTO;
import io.harness.notification.senders.PagerDutySenderImpl;
import io.harness.notification.service.PagerDutyServiceImpl.PagerDutyTemplate;
import io.harness.notification.service.api.NotificationSettingsService;
import io.harness.notification.service.api.NotificationTemplateService;
import io.harness.rule.Owner;
import io.harness.serializer.YamlUtils;
import io.harness.service.DelegateGrpcClientWrapper;

import com.fasterxml.jackson.core.type.TypeReference;
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
public class PagerDutyServiceImplTest extends CategoryTest {
  @Mock private NotificationSettingsService notificationSettingsService;
  @Mock private NotificationTemplateService notificationTemplateService;
  @Mock private YamlUtils yamlUtils;
  @Mock private PagerDutySenderImpl pagerDutySender;
  @Mock private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
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
        pagerDutySender, delegateGrpcClientWrapper);
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
    when(pagerDutySender.send(any(), any(), any(), any())).thenReturn(notificationExpectedResponse);
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
    when(pagerDutySender.send(any(), any(), any(), any())).thenReturn(notificationExpectedResponse);
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
    when(pagerDutySender.send(any(), any(), any(), any())).thenReturn(notificationExpectedResponse);
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
    when(pagerDutySender.send(any(), any(), any(), any())).thenReturn(notificationExpectedResponse);
    when(notificationTemplateService.getTemplateAsString(any(), any()))
        .thenReturn(Optional.of("This is a test notification"));
    when(yamlUtils.read(any(), (TypeReference<PagerDutyTemplate>) any())).thenReturn(pdTemplate);
    boolean response = pagerdutyService.sendTestNotification(notificationSettingDTO4);
    assertTrue(response);
  }
}

/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.freeze.helpers;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.encryption.Scope;
import io.harness.freeze.beans.FreezeNotifications;
import io.harness.freeze.beans.FreezeType;
import io.harness.freeze.beans.FreezeWindow;
import io.harness.freeze.beans.response.FreezeSummaryResponseDTO;
import io.harness.freeze.beans.yaml.FreezeInfoConfig;
import io.harness.freeze.entity.FreezeConfigEntity;
import io.harness.freeze.notifications.NotificationHelper;
import io.harness.notification.FreezeEventType;
import io.harness.notification.notificationclient.NotificationClient;
import io.harness.notification.notificationclient.NotificationResultWithStatus;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.rule.Owner;
import io.harness.utils.NGFeatureFlagHelperService;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDC)
public class NotificationHelperTest extends CategoryTest {
  @InjectMocks NotificationHelper notificationHelper;
  @Mock NotificationClient notificationClient;
  @Mock NGFeatureFlagHelperService ngFeatureFlagHelperService;

  private final String ACCOUNT_ID = "accountId";
  private final String ORG_IDENTIFIER = "oId";
  private final String PROJ_IDENTIFIER = "pId";
  private final String FREEZE_IDENTIFIER = "freezeId";

  private String yaml;

  private String yamlWithoutNotifications;

  FreezeConfigEntity freezeConfigEntity;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    ClassLoader classLoader = this.getClass().getClassLoader();
    String filename = "projectFreezeConfig.yaml";
    yaml = Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    yamlWithoutNotifications = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("FreezeWithoutNotifications.yaml")), StandardCharsets.UTF_8);

    freezeConfigEntity = FreezeConfigEntity.builder()
                             .accountId(ACCOUNT_ID)
                             .orgIdentifier(ORG_IDENTIFIER)
                             .projectIdentifier(PROJ_IDENTIFIER)
                             .identifier(FREEZE_IDENTIFIER)
                             .name(FREEZE_IDENTIFIER)
                             .yaml(yaml)
                             .type(FreezeType.MANUAL)
                             .freezeScope(Scope.PROJECT)
                             .build();
    when(ngFeatureFlagHelperService.isEnabled(any(), any())).thenReturn(true);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testsendNotification() throws IOException {
    when(notificationClient.sendNotificationAsync(any())).thenReturn(NotificationResultWithStatus.builder().build());
    notificationHelper.sendNotification(yaml, true, true, null, ACCOUNT_ID, "", "", false);
    verify(notificationClient, times(6)).sendNotificationAsync(any());
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testSendNotificationForFreezeConfigs_WithoutException() {
    FreezeSummaryResponseDTO manualFreezeSummaryResponseDTO = FreezeSummaryResponseDTO.builder()
                                                                  .accountId(ACCOUNT_ID)
                                                                  .freezeScope(Scope.PROJECT)
                                                                  .identifier(FREEZE_IDENTIFIER)
                                                                  .orgIdentifier(ORG_IDENTIFIER)
                                                                  .projectIdentifier(PROJ_IDENTIFIER)
                                                                  .yaml(yaml)
                                                                  .type(FreezeType.MANUAL)
                                                                  .build();
    FreezeSummaryResponseDTO globalFreezeSummaryResponseDTO = FreezeSummaryResponseDTO.builder()
                                                                  .accountId(ACCOUNT_ID)
                                                                  .freezeScope(Scope.PROJECT)
                                                                  .identifier(FREEZE_IDENTIFIER)
                                                                  .orgIdentifier(ORG_IDENTIFIER)
                                                                  .projectIdentifier(PROJ_IDENTIFIER)
                                                                  .yaml(yaml)
                                                                  .type(FreezeType.GLOBAL)
                                                                  .build();
    notificationHelper.sendNotificationForFreezeConfigs(Collections.singletonList(manualFreezeSummaryResponseDTO),
        Collections.singletonList(globalFreezeSummaryResponseDTO), Ambiance.newBuilder().build(), "executionUrl",
        "baseUrl");
    verify(notificationClient, times(6)).sendNotificationAsync(any());
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testSendNotificationForFreezeConfigs_WithException() {
    FreezeSummaryResponseDTO manualFreezeSummaryResponseDTO = FreezeSummaryResponseDTO.builder()
                                                                  .accountId(ACCOUNT_ID)
                                                                  .freezeScope(Scope.PROJECT)
                                                                  .identifier(FREEZE_IDENTIFIER)
                                                                  .orgIdentifier(ORG_IDENTIFIER)
                                                                  .projectIdentifier(PROJ_IDENTIFIER)
                                                                  .yaml("yaml")
                                                                  .type(FreezeType.MANUAL)
                                                                  .build();
    FreezeSummaryResponseDTO globalFreezeSummaryResponseDTO = FreezeSummaryResponseDTO.builder()
                                                                  .accountId(ACCOUNT_ID)
                                                                  .freezeScope(Scope.PROJECT)
                                                                  .identifier(FREEZE_IDENTIFIER)
                                                                  .orgIdentifier(ORG_IDENTIFIER)
                                                                  .projectIdentifier(PROJ_IDENTIFIER)
                                                                  .yaml("yaml")
                                                                  .type(FreezeType.GLOBAL)
                                                                  .build();
    assertThatCode(()
                       -> notificationHelper.sendNotificationForFreezeConfigs(
                           Collections.singletonList(manualFreezeSummaryResponseDTO),
                           Collections.singletonList(globalFreezeSummaryResponseDTO), Ambiance.newBuilder().build(),
                           "executionUrl", "baseUrl"))
        .doesNotThrowAnyException();

    FreezeSummaryResponseDTO manualFreezeSummaryResponseDTO1 = FreezeSummaryResponseDTO.builder()
                                                                   .accountId(ACCOUNT_ID)
                                                                   .freezeScope(Scope.PROJECT)
                                                                   .identifier(FREEZE_IDENTIFIER)
                                                                   .orgIdentifier(ORG_IDENTIFIER)
                                                                   .projectIdentifier(PROJ_IDENTIFIER)
                                                                   .type(FreezeType.MANUAL)
                                                                   .build();
    FreezeSummaryResponseDTO globalFreezeSummaryResponseDTO1 = FreezeSummaryResponseDTO.builder()
                                                                   .accountId(ACCOUNT_ID)
                                                                   .freezeScope(Scope.PROJECT)
                                                                   .identifier(FREEZE_IDENTIFIER)
                                                                   .orgIdentifier(ORG_IDENTIFIER)
                                                                   .projectIdentifier(PROJ_IDENTIFIER)
                                                                   .type(FreezeType.GLOBAL)
                                                                   .build();
    assertThatCode(()
                       -> notificationHelper.sendNotificationForFreezeConfigs(
                           Collections.singletonList(manualFreezeSummaryResponseDTO1),
                           Collections.singletonList(globalFreezeSummaryResponseDTO1), Ambiance.newBuilder().build(),
                           "executionUrl", "baseUrl"))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testGetFreezeUrls() {
    String baseUrl = "baseUrl";
    assertThat(notificationHelper.getGlobalFreezeUrl(baseUrl, null, ACCOUNT_ID)).isEmpty();
    assertThat(notificationHelper.getManualFreezeUrl(baseUrl, null, ACCOUNT_ID)).isEmpty();
    FreezeInfoConfig freezeInfoConfig1 = FreezeInfoConfig.builder()
                                             .orgIdentifier(ORG_IDENTIFIER)
                                             .projectIdentifier(PROJ_IDENTIFIER)
                                             .identifier(FREEZE_IDENTIFIER)
                                             .build();
    FreezeInfoConfig freezeInfoConfig2 =
        FreezeInfoConfig.builder().orgIdentifier(ORG_IDENTIFIER).identifier(FREEZE_IDENTIFIER).build();
    FreezeInfoConfig freezeInfoConfig3 = FreezeInfoConfig.builder().identifier(FREEZE_IDENTIFIER).build();
    assertThat(notificationHelper.getManualFreezeUrl(baseUrl, freezeInfoConfig1, ACCOUNT_ID))
        .isEqualTo("baseUrl/account/accountId/cd/orgs/oId/projects/pId/setup/freeze-window-studio/window/freezeId");
    assertThat(notificationHelper.getManualFreezeUrl(baseUrl, freezeInfoConfig2, ACCOUNT_ID))
        .isEqualTo("baseUrl/account/accountId/settings/organizations/oId/setup/freeze-window-studio/window/freezeId");
    assertThat(notificationHelper.getManualFreezeUrl(baseUrl, freezeInfoConfig3, ACCOUNT_ID))
        .isEqualTo("baseUrl/account/accountId/settings/freeze-window-studio/window/freezeId");
    assertThat(notificationHelper.getGlobalFreezeUrl(baseUrl, freezeInfoConfig1, ACCOUNT_ID))
        .isEqualTo("baseUrl/account/accountId/cd/orgs/oId/projects/pId/setup/freeze-windows");
    assertThat(notificationHelper.getGlobalFreezeUrl(baseUrl, freezeInfoConfig2, ACCOUNT_ID))
        .isEqualTo("baseUrl/account/accountId/settings/organizations/oId/setup/freeze-windows");
    assertThat(notificationHelper.getGlobalFreezeUrl(baseUrl, freezeInfoConfig3, ACCOUNT_ID))
        .isEqualTo("baseUrl/account/accountId/settings/freeze-windows");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testConstructTemplateData() {
    FreezeNotifications freezeNotifications = FreezeNotifications.builder().customizedMessage("message").build();
    FreezeInfoConfig freezeInfoConfig1 = FreezeInfoConfig.builder()
                                             .orgIdentifier(ORG_IDENTIFIER)
                                             .projectIdentifier(PROJ_IDENTIFIER)
                                             .name(FREEZE_IDENTIFIER)
                                             .windows(Collections.emptyList())
                                             .notifications(Collections.singletonList(freezeNotifications))
                                             .build();
    Map<String, String> templateData =
        notificationHelper.constructTemplateData(FreezeEventType.DEPLOYMENT_REJECTED_DUE_TO_FREEZE, freezeInfoConfig1,
            Ambiance.newBuilder().build(), ACCOUNT_ID, "executionUrl", "baseUrl", true, freezeNotifications);
    assertThat(templateData.size()).isEqualTo(6);
    assertThat(templateData.get("BLACKOUT_WINDOW_URL"))
        .isEqualTo("baseUrl/account/accountId/cd/orgs/oId/projects/pId/setup/freeze-windows");
    assertThat(templateData.get("BLACKOUT_WINDOW_NAME")).isEqualTo(FREEZE_IDENTIFIER);
    assertThat(templateData.get("WORKFLOW_URL")).isEqualTo("executionUrl");
    assertThat(templateData.get("CUSTOMIZED_MESSAGE")).isEqualTo(" message");

    FreezeWindow freezeWindow = new FreezeWindow();
    freezeWindow.setTimeZone("Asia/Calcutta");
    freezeWindow.setStartTime("2025-01-19 04:35 PM");
    freezeWindow.setDuration("30m");
    FreezeInfoConfig freezeInfoConfig2 = FreezeInfoConfig.builder()
                                             .orgIdentifier(ORG_IDENTIFIER)
                                             .projectIdentifier(PROJ_IDENTIFIER)
                                             .name(FREEZE_IDENTIFIER)
                                             .windows(Collections.singletonList(freezeWindow))
                                             .notifications(Collections.singletonList(freezeNotifications))
                                             .build();
    Map<String, String> templateData2 =
        notificationHelper.constructTemplateData(FreezeEventType.DEPLOYMENT_REJECTED_DUE_TO_FREEZE, freezeInfoConfig2,
            Ambiance.newBuilder().build(), ACCOUNT_ID, "executionUrl", "baseUrl", true, freezeNotifications);
    assertThat(templateData2.size()).isEqualTo(9);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testsendNotification_2() throws IOException {
    when(notificationClient.sendNotificationAsync(any())).thenReturn(NotificationResultWithStatus.builder().build());
    notificationHelper.sendNotification(yamlWithoutNotifications, false, true, null, ACCOUNT_ID, "", "", false);
    verify(notificationClient, times(2)).sendNotificationAsync(any());
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testsendNotification_3() throws IOException {
    when(notificationClient.sendNotificationAsync(any())).thenReturn(NotificationResultWithStatus.builder().build());
    notificationHelper.sendNotification(yamlWithoutNotifications, true, false, null, ACCOUNT_ID, "", "", false);
    verify(notificationClient, times(3)).sendNotificationAsync(any());
  }
}

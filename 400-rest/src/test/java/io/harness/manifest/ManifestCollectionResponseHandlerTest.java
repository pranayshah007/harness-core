/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.manifest;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.PRABU;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.MANIFEST_ID;
import static software.wings.utils.WingsTestConstants.PERPETUAL_TASK_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import software.wings.beans.alert.AlertType;
import software.wings.beans.alert.ManifestCollectionFailedAlert;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.HelmChart;
import software.wings.beans.appmanifest.StoreType;
import software.wings.delegatetasks.manifest.ManifestCollectionExecutionResponse;
import software.wings.delegatetasks.manifest.ManifestCollectionResponse;
import software.wings.service.impl.applicationmanifest.AppManifestPTaskHelper;
import software.wings.service.intfc.AlertService;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.TriggerService;
import software.wings.service.intfc.applicationmanifest.HelmChartService;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDC)
@TargetModule(HarnessModule._815_CG_TRIGGERS)
public class ManifestCollectionResponseHandlerTest extends CategoryTest {
  @Mock AppManifestPTaskHelper appManifestPTaskHelper;
  @Mock ApplicationManifestService applicationManifestService;
  @Mock FeatureFlagService featureFlagService;
  @Mock AlertService alertService;
  @Mock HelmChartService helmChartService;
  @Mock TriggerService triggerService;

  @Inject @InjectMocks ManifestCollectionResponseHandler manifestCollectionResponseHandler;

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Before
  public void setup() {
    when(featureFlagService.isEnabled(eq(FeatureName.HELM_CHART_AS_ARTIFACT), any())).thenReturn(true);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldHandleInvalidAppManifest() {
    ManifestCollectionExecutionResponse response =
        ManifestCollectionExecutionResponse.builder().appManifestId(MANIFEST_ID + 2).build();
    manifestCollectionResponseHandler.handleManifestCollectionResponse(ACCOUNT_ID, PERPETUAL_TASK_ID, response);
    verify(appManifestPTaskHelper).deletePerpetualTask(PERPETUAL_TASK_ID, MANIFEST_ID + 2, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldHandleFailureResponse() {
    ManifestCollectionExecutionResponse response = ManifestCollectionExecutionResponse.builder()
                                                       .appManifestId(MANIFEST_ID)
                                                       .appId(APP_ID)
                                                       .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                                       .build();
    ApplicationManifest appManifest = ApplicationManifest.builder()
                                          .accountId(ACCOUNT_ID)
                                          .storeType(StoreType.Remote)
                                          .perpetualTaskId(PERPETUAL_TASK_ID)
                                          .failedAttempts(ManifestCollectionResponseHandler.MAX_FAILED_ATTEMPTS - 2)
                                          .build();
    appManifest.setUuid(MANIFEST_ID);
    doReturn(appManifest).when(applicationManifestService).getById(APP_ID, MANIFEST_ID);
    manifestCollectionResponseHandler.handleManifestCollectionResponse(ACCOUNT_ID, PERPETUAL_TASK_ID, response);
    verify(applicationManifestService, times(1))
        .updateFailedAttempts(ACCOUNT_ID, MANIFEST_ID, ManifestCollectionResponseHandler.MAX_FAILED_ATTEMPTS - 1);

    appManifest.setFailedAttempts(appManifest.getFailedAttempts() + 1);
    appManifest.setAppId(APP_ID);
    manifestCollectionResponseHandler.handleManifestCollectionResponse(ACCOUNT_ID, PERPETUAL_TASK_ID, response);
    verify(appManifestPTaskHelper).resetPerpetualTask(appManifest);
    verify(alertService)
        .openAlert(eq(ACCOUNT_ID), eq(APP_ID), eq(AlertType.MANIFEST_COLLECTION_FAILED),
            any(ManifestCollectionFailedAlert.class));
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldHandleSuccessResponse() {
    List<HelmChart> helmCharts = IntStream.rangeClosed(1, 3)
                                     .boxed()
                                     .map(i -> generateHelmChartWithVersion(String.valueOf(i)))
                                     .collect(Collectors.toList());
    Set<String> toBeDeletedCharts = new HashSet<>(Arrays.asList("4", "5"));
    ManifestCollectionResponse manifestCollectionResponse =
        ManifestCollectionResponse.builder()
            .stable(true)
            .helmCharts(helmCharts.stream().map(HelmChart::toDto).collect(Collectors.toList()))
            .toBeDeletedKeys(toBeDeletedCharts)
            .build();
    ManifestCollectionExecutionResponse response = ManifestCollectionExecutionResponse.builder()
                                                       .appManifestId(MANIFEST_ID)
                                                       .appId(APP_ID)
                                                       .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                       .manifestCollectionResponse(manifestCollectionResponse)
                                                       .build();
    ApplicationManifest appManifest = ApplicationManifest.builder()
                                          .accountId(ACCOUNT_ID)
                                          .storeType(StoreType.Remote)
                                          .perpetualTaskId(PERPETUAL_TASK_ID)
                                          .failedAttempts(5)
                                          .build();
    appManifest.setUuid(MANIFEST_ID);
    appManifest.setAppId(APP_ID);

    doReturn(appManifest).when(applicationManifestService).getById(APP_ID, MANIFEST_ID);
    doReturn(true).when(helmChartService).deleteHelmChartsByVersions(ACCOUNT_ID, MANIFEST_ID, toBeDeletedCharts);
    doReturn(true).when(helmChartService).addCollectedHelmCharts(ACCOUNT_ID, MANIFEST_ID, helmCharts);
    manifestCollectionResponseHandler.handleManifestCollectionResponse(ACCOUNT_ID, PERPETUAL_TASK_ID, response);

    verify(applicationManifestService, times(1)).updateFailedAttempts(ACCOUNT_ID, MANIFEST_ID, 0);
    verify(helmChartService, times(1)).deleteHelmChartsByVersions(ACCOUNT_ID, MANIFEST_ID, toBeDeletedCharts);
    verify(helmChartService, times(1)).addCollectedHelmCharts(ACCOUNT_ID, MANIFEST_ID, helmCharts);
    verify(triggerService, times(1)).triggerExecutionPostManifestCollectionAsync(eq(APP_ID), eq(MANIFEST_ID), any());
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldHandleSuccessResponseWithOnlyCollection() {
    List<HelmChart> helmCharts = IntStream.rangeClosed(1, 3)
                                     .boxed()
                                     .map(i -> generateHelmChartWithVersion(String.valueOf(i)))
                                     .collect(Collectors.toList());
    ManifestCollectionResponse manifestCollectionResponse =
        ManifestCollectionResponse.builder()
            .stable(true)
            .helmCharts(helmCharts.stream().map(HelmChart::toDto).collect(Collectors.toList()))
            .build();
    ManifestCollectionExecutionResponse response = ManifestCollectionExecutionResponse.builder()
                                                       .appManifestId(MANIFEST_ID)
                                                       .appId(APP_ID)
                                                       .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                       .manifestCollectionResponse(manifestCollectionResponse)
                                                       .build();
    ApplicationManifest appManifest = generateApplicationManifest();
    appManifest.setAppId(APP_ID);
    appManifest.setUuid(MANIFEST_ID);

    doReturn(appManifest).when(applicationManifestService).getById(APP_ID, MANIFEST_ID);
    doReturn(true).when(helmChartService).addCollectedHelmCharts(ACCOUNT_ID, MANIFEST_ID, helmCharts);

    manifestCollectionResponseHandler.handleManifestCollectionResponse(ACCOUNT_ID, PERPETUAL_TASK_ID, response);
    verify(helmChartService, never()).deleteHelmChartsByVersions(anyString(), anyString(), any());
    verify(helmChartService, times(1)).addCollectedHelmCharts(ACCOUNT_ID, MANIFEST_ID, helmCharts);
    verify(triggerService, times(1)).triggerExecutionPostManifestCollectionAsync(eq(APP_ID), eq(MANIFEST_ID), any());
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldHandleSuccessResponseWithOnlyCleanup() {
    Set<String> toBeDeletedCharts = new HashSet<>(Arrays.asList("4", "5"));
    ManifestCollectionResponse manifestCollectionResponse =
        ManifestCollectionResponse.builder().stable(true).toBeDeletedKeys(toBeDeletedCharts).build();
    ManifestCollectionExecutionResponse response = ManifestCollectionExecutionResponse.builder()
                                                       .appManifestId(MANIFEST_ID)
                                                       .appId(APP_ID)
                                                       .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                       .manifestCollectionResponse(manifestCollectionResponse)
                                                       .build();
    ApplicationManifest appManifest = generateApplicationManifest();
    appManifest.setAppId(APP_ID);
    appManifest.setUuid(MANIFEST_ID);

    doReturn(appManifest).when(applicationManifestService).getById(APP_ID, MANIFEST_ID);
    doReturn(true).when(helmChartService).deleteHelmChartsByVersions(ACCOUNT_ID, MANIFEST_ID, toBeDeletedCharts);

    manifestCollectionResponseHandler.handleManifestCollectionResponse(ACCOUNT_ID, PERPETUAL_TASK_ID, response);
    verify(helmChartService, times(1)).deleteHelmChartsByVersions(ACCOUNT_ID, MANIFEST_ID, toBeDeletedCharts);
    verify(helmChartService, never()).addCollectedHelmCharts(anyString(), anyString(), any());
    verify(triggerService, never()).triggerExecutionPostManifestCollectionAsync(eq(APP_ID), eq(MANIFEST_ID), any());
  }

  private ApplicationManifest generateApplicationManifest() {
    ApplicationManifest appManifest = ApplicationManifest.builder()
                                          .accountId(ACCOUNT_ID)
                                          .storeType(StoreType.Remote)
                                          .perpetualTaskId(PERPETUAL_TASK_ID)
                                          .failedAttempts(5)
                                          .build();
    appManifest.setUuid(MANIFEST_ID);
    return appManifest;
  }

  private HelmChart generateHelmChartWithVersion(String version) {
    return HelmChart.builder()
        .accountId(ACCOUNT_ID)
        .appId(APP_ID)
        .uuid(UUID + version)
        .applicationManifestId(MANIFEST_ID)
        .serviceId(SERVICE_ID)
        .version(version)
        .build();
  }
}

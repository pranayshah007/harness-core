/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.manifest;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static java.util.stream.Collectors.toSet;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.FeatureName;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.logging.CommandExecutionStatus;
import io.harness.perpetualtask.PerpetualTaskLogContext;
import io.harness.perpetualtask.PerpetualTaskService;

import software.wings.beans.alert.AlertType;
import software.wings.beans.alert.ManifestCollectionFailedAlert;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.HelmChart;
import software.wings.delegatetasks.manifest.ApplicationManifestLogContext;
import software.wings.delegatetasks.manifest.ManifestCollectionExecutionResponse;
import software.wings.delegatetasks.manifest.ManifestCollectionResponse;
import software.wings.service.impl.applicationmanifest.AppManifestPTaskHelper;
import software.wings.service.impl.applicationmanifest.ManifestCollectionUtils;
import software.wings.service.intfc.AlertService;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.TriggerService;
import software.wings.service.intfc.applicationmanifest.HelmChartService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_FIRST_GEN})
@OwnedBy(CDC)
@Slf4j
@Singleton
@TargetModule(HarnessModule._815_CG_TRIGGERS)
public class ManifestCollectionResponseHandler {
  private static final int MAX_MANIFEST_COLLECTION_FOR_WARN = 100;
  private static final int MAX_LOGS = 100;
  public static final int MAX_FAILED_ATTEMPTS = 3500;

  @Inject private ApplicationManifestService applicationManifestService;
  @Inject private AppManifestPTaskHelper appManifestPTaskHelper;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private PerpetualTaskService perpetualTaskService;
  @Inject private AlertService alertService;
  @Inject private ManifestCollectionUtils manifestCollectionUtils;
  @Inject private HelmChartService helmChartService;
  @Inject private TriggerService triggerService;

  public void handleManifestCollectionResponse(@NotNull String accountId, @NotNull String perpetualTaskId,
      ManifestCollectionExecutionResponse executionResponse) {
    final String appManifestId = executionResponse.getAppManifestId();
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new PerpetualTaskLogContext(perpetualTaskId, OVERRIDE_ERROR)) {
      ApplicationManifest appManifest = applicationManifestService.getById(executionResponse.getAppId(), appManifestId);
      if (appManifest == null || !perpetualTaskId.equals(appManifest.getPerpetualTaskId())) {
        log.warn("Invalid perpetual task found for app manifest: {}", appManifestId);
        appManifestPTaskHelper.deletePerpetualTask(perpetualTaskId, appManifestId, accountId);
        return;
      }
      try (AutoLogContext ignore3 = new ApplicationManifestLogContext(appManifestId, OVERRIDE_ERROR)) {
        if (!featureFlagService.isEnabled(FeatureName.HELM_CHART_AS_ARTIFACT, accountId)) {
          return;
        }

        if (executionResponse.getCommandExecutionStatus() == CommandExecutionStatus.SUCCESS) {
          handleSuccessResponse(appManifest, executionResponse.getManifestCollectionResponse());
        } else {
          handleFailureResponse(appManifest, perpetualTaskId);
        }
      }
    }
  }

  private void handleFailureResponse(ApplicationManifest appManifest, String perpetualTaskId) {
    int failedAttempts = appManifest.getFailedAttempts() + 1;
    if (failedAttempts % 25 == 0) {
      appManifestPTaskHelper.resetPerpetualTask(appManifest);
    }

    String appManifestId = appManifest.getUuid();
    String accountId = appManifest.getAccountId();
    applicationManifestService.updateFailedAttempts(accountId, appManifestId, failedAttempts);

    log.warn("Failed to collect manifest versions, total failed attempts: {} [serviceId={}]", failedAttempts,
        appManifest.getServiceId());
    if (failedAttempts == MAX_FAILED_ATTEMPTS) {
      ManifestCollectionFailedAlert failedAlert = ManifestCollectionFailedAlert.builder()
                                                      .appId(appManifest.getAppId())
                                                      .serviceId(appManifest.getServiceId())
                                                      .appManifestId(appManifestId)
                                                      .build();

      alertService.openAlert(accountId, appManifest.getAppId(), AlertType.MANIFEST_COLLECTION_FAILED, failedAlert);
      appManifestPTaskHelper.deletePerpetualTask(perpetualTaskId, appManifestId, accountId);
    }
  }

  private void handleSuccessResponse(
      ApplicationManifest appManifest, ManifestCollectionResponse manifestCollectionResponse) {
    String appManifestId = appManifest.getUuid();
    String accountId = appManifest.getAccountId();
    applicationManifestService.updateFailedAttempts(accountId, appManifestId, 0);
    alertService.closeAlert(appManifest.getAccountId(), null, AlertType.MANIFEST_COLLECTION_FAILED,
        ManifestCollectionFailedAlert.builder().appManifestId(appManifest.getUuid()).build());
    List<HelmChart> manifestsCollected = HelmChart.fromDtos(manifestCollectionResponse.getHelmCharts());
    Set<String> toBeDeletedVersions = manifestCollectionResponse.getToBeDeletedKeys();

    if (isNotEmpty(toBeDeletedVersions)) {
      if (!helmChartService.deleteHelmChartsByVersions(accountId, appManifestId, toBeDeletedVersions)) {
        log.error("Failed to delete manifest versions: {}", toBeDeletedVersions);
      } else {
        log.info("Deleted manifest versions {}, count = {} ", toBeDeletedVersions, toBeDeletedVersions.size());
      }
    }

    if (isNotEmpty(manifestsCollected)) {
      if (!helmChartService.addCollectedHelmCharts(accountId, appManifestId, manifestsCollected)) {
        log.error("Error in saving one or more collected manifest versions: {}",
            manifestsCollected.stream().map(HelmChart::getVersion).collect(toSet()));
        return;
      }

      if (manifestsCollected.size() > MAX_MANIFEST_COLLECTION_FOR_WARN) {
        log.warn("Collected {} versions in single attempt", manifestsCollected.size());
      }
      manifestsCollected.stream().limit(MAX_LOGS).forEach(
          manifest -> log.info("Collected new version {}", manifest.getVersion()));

      if (featureFlagService.isEnabled(FeatureName.HELM_CHART_AS_ARTIFACT, accountId)) {
        if (manifestCollectionResponse.isStable()) {
          triggerService.triggerExecutionPostManifestCollectionAsync(
              appManifest.getAppId(), appManifestId, manifestsCollected);
        }
      }
    }
  }
}

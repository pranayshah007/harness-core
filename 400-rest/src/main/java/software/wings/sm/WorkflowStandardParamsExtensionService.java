/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;

import software.wings.beans.Account;
import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.appmanifest.HelmChart;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamServiceBindingService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.applicationmanifest.HelmChartService;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * The Class that extends the functionality of WorflowStandardParams.
 */

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_FIRST_GEN})
@Slf4j
@OwnedBy(CDC)
public class WorkflowStandardParamsExtensionService {
  private final AppService appService;
  private final AccountService accountService;
  private final ArtifactService artifactService;
  private final EnvironmentService environmentService;
  private final ArtifactStreamServiceBindingService artifactStreamServiceBindingService;
  private final HelmChartService helmChartService;
  private final FeatureFlagService featureFlagService;

  @Inject
  public WorkflowStandardParamsExtensionService(AppService appService, AccountService accountService,
      ArtifactService artifactService, EnvironmentService environmentService,
      ArtifactStreamServiceBindingService artifactStreamServiceBindingService, HelmChartService helmChartService,
      FeatureFlagService featureFlagService) {
    this.appService = appService;
    this.accountService = accountService;
    this.artifactService = artifactService;
    this.environmentService = environmentService;
    this.artifactStreamServiceBindingService = artifactStreamServiceBindingService;
    this.helmChartService = helmChartService;
    this.featureFlagService = featureFlagService;
  }

  public HelmChart getHelmChartForService(WorkflowStandardParams workflowStandardParams, String serviceId) {
    List<HelmChart> helmCharts = getHelmCharts(workflowStandardParams);
    if (isEmpty(helmCharts)) {
      return null;
    }

    return helmCharts.stream().filter(helmChart -> serviceId.equals(helmChart.getServiceId())).findFirst().orElse(null);
  }

  public List<HelmChart> getHelmCharts(WorkflowStandardParams workflowStandardParams) {
    if (isEmpty(workflowStandardParams.getHelmChartIds())) {
      return null;
    }

    return helmChartService.listByIds(
        getApp(workflowStandardParams).getAccountId(), workflowStandardParams.getHelmChartIds());
  }

  /**
   * Gets app.
   *
   * @return the app
   */
  public Application getApp(WorkflowStandardParams workflowStandardParams) {
    if (workflowStandardParams.getAppId() == null) {
      return null;
    }
    return appService.getApplicationWithDefaults(workflowStandardParams.getAppId());
  }

  public Application fetchRequiredApp(WorkflowStandardParams workflowStandardParams) {
    Application application = getApp(workflowStandardParams);
    if (application == null) {
      throw new InvalidRequestException("App cannot be null");
    }
    return application;
  }

  public Account getAccount(WorkflowStandardParams workflowStandardParams) {
    Application application = getApp(workflowStandardParams);
    String accountId = application == null ? null : application.getAccountId();
    if (accountId == null) {
      return null;
    }

    return accountService.getAccountWithDefaults(accountId);
  }

  /**
   * Gets env.
   *
   * @return the env
   */
  public Environment getEnv(WorkflowStandardParams workflowStandardParams) {
    if (workflowStandardParams.getEnvId() == null) {
      return null;
    }

    return environmentService.get(workflowStandardParams.getAppId(), workflowStandardParams.getEnvId(), false);
  }

  public Environment fetchRequiredEnv(WorkflowStandardParams workflowStandardParams) {
    Environment environment = getEnv(workflowStandardParams);
    if (environment == null) {
      throw new InvalidRequestException("Env cannot be null");
    }
    return environment;
  }

  /**
   * Gets artifacts.
   *
   * @return the artifacts
   */
  public List<Artifact> getArtifacts(WorkflowStandardParams workflowStandardParams) {
    if (isEmpty(workflowStandardParams.getArtifactIds())) {
      return null;
    }

    List<Artifact> list = new ArrayList<>();
    for (String artifactId : workflowStandardParams.getArtifactIds()) {
      Artifact artifact = artifactService.get(artifactId);
      if (artifact != null) {
        list.add(artifact);
      }
    }

    if (workflowStandardParams.getAppId() != null) {
      String accountId = appService.getAccountIdByAppId(workflowStandardParams.getAppId());
      if (featureFlagService.isEnabled(FeatureName.SORT_ARTIFACTS_IN_UPDATED_ORDER, accountId)) {
        log.info("Sorting collected artifacts by lastUpdatedAt");
        list.sort(Comparator.comparing(Artifact::getLastUpdatedAt).reversed());
      }
    }

    return list;
  }

  /**
   * Gets rollback artifacts.
   *
   * @return the rollback artifacts
   */
  public List<Artifact> getRollbackArtifacts(WorkflowStandardParams workflowStandardParams) {
    if (isEmpty(workflowStandardParams.getRollbackArtifactIds())) {
      return null;
    }

    List<Artifact> list = new ArrayList<>();
    for (String rollbackArtifactId : workflowStandardParams.getRollbackArtifactIds()) {
      Artifact rollbackArtifact = artifactService.get(rollbackArtifactId);
      if (rollbackArtifact != null) {
        list.add(rollbackArtifact);
      }
    }

    return list;
  }

  /**
   * Gets artifact for service.
   *
   * @param serviceId the service id
   * @return the artifact for service
   */
  public Artifact getArtifactForService(WorkflowStandardParams workflowStandardParams, String serviceId) {
    List<Artifact> artifacts = getArtifacts(workflowStandardParams);
    if (isEmpty(artifacts)) {
      return null;
    }

    List<String> artifactStreamIds = artifactStreamServiceBindingService.listArtifactStreamIds(serviceId);
    if (isEmpty(artifactStreamIds)) {
      return null;
    }

    return artifacts.stream()
        .filter(artifact -> artifactStreamIds.contains(artifact.getArtifactStreamId()))
        .findFirst()
        .orElse(null);
  }

  /**
   * Gets rollback artifact for service.
   *
   * @param serviceId the service id
   * @return the rollback artifact for service
   */
  public Artifact getRollbackArtifactForService(WorkflowStandardParams workflowStandardParams, String serviceId) {
    List<Artifact> rollbackArtifacts = getRollbackArtifacts(workflowStandardParams);
    if (isEmpty(rollbackArtifacts)) {
      return null;
    }

    List<String> rollbackArtifactStreamIds = artifactStreamServiceBindingService.listArtifactStreamIds(serviceId);
    if (isEmpty(rollbackArtifactStreamIds)) {
      return null;
    }

    return rollbackArtifacts.stream()
        .filter(rollbackArtifact -> rollbackArtifactStreamIds.contains(rollbackArtifact.getArtifactStreamId()))
        .findFirst()
        .orElse(null);
  }
}

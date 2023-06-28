/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.helm.request;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.task.helm.HelmCommandFlag;
import io.harness.helm.HelmCommandType;
import io.harness.k8s.model.HelmVersion;
import io.harness.logging.LogCallback;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.GitConfig;
import software.wings.beans.GitFileConfig;
import software.wings.beans.dto.HelmChartSpecification;
import software.wings.delegatetasks.validation.capabilities.HelmCommandRequest;
import software.wings.helpers.ext.k8s.request.K8sDelegateManifestConfig;
import software.wings.service.impl.ContainerServiceParams;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Created by anubhaw on 3/22/18.
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TargetModule(HarnessModule._950_DELEGATE_TASKS_BEANS)
@OwnedBy(CDP)
public class HelmRollbackCommandRequest extends HelmCommandRequest {
  private Integer newReleaseVersion;
  private Integer prevReleaseVersion;
  private Integer rollbackVersion;
  private long timeoutInMillis;

  public HelmRollbackCommandRequest(boolean mergeCapabilities) {
    super(HelmCommandType.ROLLBACK, mergeCapabilities);
  }

  @Builder
  public HelmRollbackCommandRequest(String accountId, String appId, String kubeConfigLocation, String commandName,
      String activityId, ContainerServiceParams containerServiceParams, String releaseName, int newReleaseVersion,
      int prevReleaseVersion, int rollbackVersion, long timeoutInMillis, HelmChartSpecification chartSpecification,
      String repoName, GitConfig gitConfig, List<EncryptedDataDetail> encryptedDataDetails,
      LogCallback executionLogCallback, String commandFlags, HelmCommandFlag helmCommandFlag,
      K8sDelegateManifestConfig sourceRepoConfig, HelmVersion helmVersion, String ocPath, String workingDir,
      GitFileConfig gitFileConfig, List<String> variableOverridesYamlFiles, boolean k8SteadyStateCheckEnabled,
      boolean mergeCapabilities, boolean isGitHostConnectivityCheck, boolean useLatestChartMuseumVersion,
      boolean useNewKubectlVersion, String gcpKeyPath, boolean useRefactorSteadyStateCheck,
      boolean skipSteadyStateCheck) {
    super(HelmCommandType.ROLLBACK, accountId, appId, kubeConfigLocation, commandName, activityId,
        containerServiceParams, releaseName, chartSpecification, repoName, gitConfig, encryptedDataDetails,
        executionLogCallback, commandFlags, helmCommandFlag, sourceRepoConfig, helmVersion, ocPath, workingDir,
        variableOverridesYamlFiles, gitFileConfig, k8SteadyStateCheckEnabled, mergeCapabilities,
        isGitHostConnectivityCheck, useLatestChartMuseumVersion, useNewKubectlVersion, gcpKeyPath,
        useRefactorSteadyStateCheck, skipSteadyStateCheck);
    this.newReleaseVersion = newReleaseVersion;
    this.prevReleaseVersion = prevReleaseVersion;
    this.rollbackVersion = rollbackVersion;
    this.timeoutInMillis = timeoutInMillis;
  }
}

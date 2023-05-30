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
import java.util.Map;
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
public class HelmInstallCommandRequest extends HelmCommandRequest {
  private Integer newReleaseVersion;
  private Integer prevReleaseVersion;
  private String namespace;
  private long timeoutInMillis;
  private Map<String, String> valueOverrides;
  private boolean optimizedFilesFetch;
  private boolean ignoreReleaseHistFailStatus;

  public HelmInstallCommandRequest(boolean mergeCapabilities) {
    super(HelmCommandType.INSTALL, mergeCapabilities);
  }

  @Builder
  public HelmInstallCommandRequest(String accountId, String appId, String kubeConfigLocation, String commandName,
      String activityId, ContainerServiceParams containerServiceParams, String releaseName,
      HelmChartSpecification chartSpecification, int newReleaseVersion, int prevReleaseVersion, String namespace,
      long timeoutInMillis, Map<String, String> valueOverrides, List<String> variableOverridesYamlFiles,
      String repoName, GitConfig gitConfig, GitFileConfig gitFileConfig, List<EncryptedDataDetail> encryptedDataDetails,
      LogCallback executionLogCallback, String commandFlags, HelmCommandFlag helmCommandFlag,
      K8sDelegateManifestConfig sourceRepoConfig, HelmVersion helmVersion, String ocPath, String workingDir,
      boolean k8SteadyStateCheckEnabled, boolean mergeCapabilities, boolean isGitHostConnectivityCheck,
      boolean useLatestChartMuseumVersion, boolean optimizedFilesFetch, boolean useNewKubectlVersion, String gcpKeyPath,
      boolean ignoreReleaseHistFailStatus) {
    super(HelmCommandType.INSTALL, accountId, appId, kubeConfigLocation, commandName, activityId,
        containerServiceParams, releaseName, chartSpecification, repoName, gitConfig, encryptedDataDetails,
        executionLogCallback, commandFlags, helmCommandFlag, sourceRepoConfig, helmVersion, ocPath, workingDir,
        variableOverridesYamlFiles, gitFileConfig, k8SteadyStateCheckEnabled, mergeCapabilities,
        isGitHostConnectivityCheck, useLatestChartMuseumVersion, useNewKubectlVersion, gcpKeyPath);
    this.newReleaseVersion = newReleaseVersion;
    this.prevReleaseVersion = prevReleaseVersion;
    this.namespace = namespace;
    this.timeoutInMillis = timeoutInMillis;
    this.valueOverrides = valueOverrides;
    this.optimizedFilesFetch = optimizedFilesFetch;
    this.ignoreReleaseHistFailStatus = ignoreReleaseHistFailStatus;
  }
}

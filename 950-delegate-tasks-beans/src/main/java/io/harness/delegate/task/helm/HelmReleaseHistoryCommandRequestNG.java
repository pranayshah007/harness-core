/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.helm;

import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.task.k8s.K8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.ManifestDelegateConfig;
import io.harness.helm.HelmCommandType;
import io.harness.k8s.model.HelmVersion;
import io.harness.logging.LogCallback;

import software.wings.beans.ServiceHookDelegateConfig;

import java.util.List;
import lombok.Builder;

public class HelmReleaseHistoryCommandRequestNG extends HelmCommandRequestNG {
  @Builder
  public HelmReleaseHistoryCommandRequestNG(String releaseName, List<String> valuesYamlList,
      K8sInfraDelegateConfig k8sInfraDelegateConfig, ManifestDelegateConfig manifestDelegateConfig, String accountId,
      boolean k8SteadyStateCheckEnabled, boolean shouldOpenFetchFilesLogStream,
      CommandUnitsProgress commandUnitsProgress, LogCallback logCallback, String namespace, HelmVersion helmVersion,
      String commandFlags, String repoName, String workingDir, String kubeConfigLocation, String ocPath,
      String commandName, boolean useLatestKubectlVersion, String gcpKeyPath, String releaseHistoryPrefix,
      List<ServiceHookDelegateConfig> serviceHooks) {
    super(releaseName, HelmCommandType.RELEASE_HISTORY, valuesYamlList, k8sInfraDelegateConfig, manifestDelegateConfig,
        accountId, k8SteadyStateCheckEnabled, shouldOpenFetchFilesLogStream, commandUnitsProgress, logCallback,
        namespace, helmVersion, commandFlags, repoName, workingDir, kubeConfigLocation, ocPath, commandName,
        useLatestKubectlVersion, gcpKeyPath, releaseHistoryPrefix, serviceHooks, false);
  }
}

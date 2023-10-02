/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.helm;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.task.k8s.HelmChartManifestDelegateConfig;
import io.harness.helm.HelmCommandData;

import com.google.inject.Singleton;
import java.util.Optional;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_FIRST_GEN})
@Singleton
@UtilityClass
public class HelmCommandDataMapperNG {
  public HelmCommandData getHelmCmdDataNG(@NonNull HelmCommandRequestNG helmCommandRequestNG) {
    HelmChartManifestDelegateConfig helmChartManifestDelegateConfig =
        (HelmChartManifestDelegateConfig) helmCommandRequestNG.getManifestDelegateConfig();

    HelmCommandData helmCommandData =
        HelmCommandData.builder()
            .kubeConfigLocation(Optional.ofNullable(helmCommandRequestNG.getKubeConfigLocation()).orElse(""))
            .helmVersion(helmCommandRequestNG.getHelmVersion())
            .releaseName(helmCommandRequestNG.getReleaseName())
            .isRepoConfigNull(helmCommandRequestNG.getManifestDelegateConfig() == null)
            .yamlFiles(helmCommandRequestNG.getValuesYamlList())
            .logCallback(helmCommandRequestNG.getLogCallback())
            .workingDir(helmCommandRequestNG.getWorkingDir())
            .commandFlags(helmCommandRequestNG.getCommandFlags())
            .repoName(helmCommandRequestNG.getRepoName())
            .namespace(helmCommandRequestNG.getNamespace())
            .valueMap(helmChartManifestDelegateConfig.getHelmCommandFlag().getValueMap())
            .isHelmCmdFlagsNull(helmChartManifestDelegateConfig.getHelmCommandFlag() == null)
            .gcpKeyPath(helmCommandRequestNG.getGcpKeyPath())
            .build();

    if (helmCommandRequestNG instanceof HelmInstallCommandRequestNG) {
      HelmInstallCommandRequestNG helmInstallCommandRequest = (HelmInstallCommandRequestNG) helmCommandRequestNG;
      helmCommandData.setPrevReleaseVersion(helmInstallCommandRequest.getPrevReleaseVersion());
      helmCommandData.setNewReleaseVersion(helmInstallCommandRequest.getNewReleaseVersion());
      helmCommandData.setTimeOutInMillis(helmInstallCommandRequest.getTimeoutInMillis());
    }

    if (helmCommandRequestNG instanceof HelmRollbackCommandRequestNG) {
      HelmRollbackCommandRequestNG helmRollbackCommandRequest = (HelmRollbackCommandRequestNG) helmCommandRequestNG;
      helmCommandData.setPrevReleaseVersion(helmRollbackCommandRequest.getPrevReleaseVersion());
      helmCommandData.setNewReleaseVersion(helmRollbackCommandRequest.getNewReleaseVersion());
      helmCommandData.setTimeOutInMillis(helmRollbackCommandRequest.getTimeoutInMillis());
    }

    return helmCommandData;
  }
}

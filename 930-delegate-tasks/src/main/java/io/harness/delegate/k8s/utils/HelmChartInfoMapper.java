/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.k8s.utils;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.task.helm.HelmChartInfo;
import io.harness.k8s.releasehistory.HelmChartInfoDTO;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_K8S})
public class HelmChartInfoMapper {
  public static HelmChartInfoDTO toHelmChartInfoDTO(HelmChartInfo helmChartInfo) {
    if (helmChartInfo == null) {
      return null;
    }
    return HelmChartInfoDTO.builder()
        .name(helmChartInfo.getName())
        .repoUrl(helmChartInfo.getRepoUrl())
        .subChartPath(helmChartInfo.getSubChartPath())
        .version(helmChartInfo.getVersion())
        .build();
  }

  public static HelmChartInfo toHelmChartInfo(HelmChartInfoDTO helmChartInfoDTO) {
    if (helmChartInfoDTO == null) {
      return null;
    }
    return HelmChartInfo.builder()
        .name(helmChartInfoDTO.getName())
        .repoUrl(helmChartInfoDTO.getRepoUrl())
        .subChartPath(helmChartInfoDTO.getSubChartPath())
        .version(helmChartInfoDTO.getVersion())
        .build();
  }
}

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.service;
import static io.harness.delegate.clienttools.ClientTool.CHARTMUSEUM;
import static io.harness.delegate.clienttools.ClientTool.GO_TEMPLATE;
import static io.harness.delegate.clienttools.ClientTool.HELM;
import static io.harness.delegate.clienttools.ClientTool.KUBECTL;
import static io.harness.delegate.clienttools.ClientTool.KUSTOMIZE;
import static io.harness.delegate.clienttools.ClientTool.OC;
import static io.harness.k8s.model.HelmVersion.V2;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.clienttools.ChartmuseumVersion;
import io.harness.delegate.clienttools.ClientTool;
import io.harness.delegate.clienttools.ClientToolVersion;
import io.harness.delegate.clienttools.InstallUtils;
import io.harness.delegate.clienttools.KubectlVersion;
import io.harness.delegate.clienttools.KustomizeVersion;
import io.harness.delegate.clienttools.OcVersion;
import io.harness.exception.InvalidRequestException;
import io.harness.k8s.config.K8sGlobalConfigService;
import io.harness.k8s.model.HelmVersion;

import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

/**
 * @deprecated use {@link InstallUtils#getPath(ClientTool, ClientToolVersion)} directly instead
 */

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@Slf4j
@OwnedBy(HarnessTeam.CDP)
@Deprecated
public class K8sGlobalConfigServiceImpl implements K8sGlobalConfigService {
  @Override
  public String getKubectlPath(boolean useNewKubectlVersion) {
    return useNewKubectlVersion ? getToolPath(KUBECTL, KubectlVersion.V1_19)
                                : getToolPath(KUBECTL, KubectlVersion.V1_13);
  }

  @Override
  public String getGoTemplateClientPath() {
    return getToolPath(GO_TEMPLATE, GO_TEMPLATE.getLatestVersion());
  }

  /*
  For all helm commands run through the binary installed through InstallUtils, we want to default to v3.
   */
  public String getHelmPath(@Nullable HelmVersion helmVersion) {
    if (helmVersion == null) {
      log.error("Did not expect null value of helmVersion, defaulting to V2");
      helmVersion = V2;
    }
    log.debug("[HELM]: picked helm binary corresponding to version {}", helmVersion);
    switch (helmVersion) {
      // catch blocks will be deleted when we end up with just one helm version in the immutable delegate
      case V2:
        return getToolPath(HELM, io.harness.delegate.clienttools.HelmVersion.V2);
      case V3:
        try {
          return getToolPath(ClientTool.HELM, io.harness.delegate.clienttools.HelmVersion.V3);
        } catch (IllegalArgumentException e) {
          log.warn("Helm 3.1.2 not installed Version 3.12.0 will be used");
          return getToolPath(ClientTool.HELM, io.harness.delegate.clienttools.HelmVersion.V3_12);
        }
      case V380:
        try {
          return getToolPath(ClientTool.HELM, io.harness.delegate.clienttools.HelmVersion.V3_8);
        } catch (IllegalArgumentException e) {
          log.warn("Helm 3.8 not installed Version 3.12.0 will be used");
          return getToolPath(ClientTool.HELM, io.harness.delegate.clienttools.HelmVersion.V3_12);
        }
      default:
        throw new InvalidRequestException("Unsupported Helm Version:" + helmVersion);
    }
  }

  @Override
  public String getChartMuseumPath(final boolean useLatestVersion) {
    if (useLatestVersion) {
      return getToolPath(CHARTMUSEUM, CHARTMUSEUM.getLatestVersion());
    }
    return getToolPath(CHARTMUSEUM, ChartmuseumVersion.V0_8);
  }

  @Override
  public String getOcPath() {
    return getToolPath(OC, OcVersion.V4_2);
  }

  @Override
  public String getKustomizePath(boolean useLatestVersion) {
    if (useLatestVersion) {
      return getToolPath(KUSTOMIZE, KUSTOMIZE.getLatestVersion());
    }
    return getToolPath(KUSTOMIZE, KustomizeVersion.V3);
  }

  private String getToolPath(ClientTool tool, ClientToolVersion version) {
    return InstallUtils.getPath(tool, version);
  }
}

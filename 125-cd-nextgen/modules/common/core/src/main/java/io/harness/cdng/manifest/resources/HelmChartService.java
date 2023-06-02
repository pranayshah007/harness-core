/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.resources;

import io.harness.cdng.manifest.resources.dtos.HelmChartResponseDTO;
import io.harness.cdng.manifest.resources.dtos.HelmManifestInternalDTO;
import io.harness.cdng.manifest.yaml.HelmChartManifestOutcome;
import io.harness.delegate.beans.storeconfig.StoreDelegateConfig;

public interface HelmChartService {
  HelmChartResponseDTO getHelmChartVersionDetails(String accountId, String orgId, String projectId, String connectorId,
      String chartName, String region, String bucketName, String folderPath, String lastTag, String storeType,
      String helmVersion);

  HelmChartResponseDTO getHelmChartVersionDetailsV2(String accountId, String orgId, String projectId, String serviceRef,
      String manifestPath, String connectorId, String chartName, String region, String bucketName, String folderPath,
      String lastTag);

  HelmManifestInternalDTO locateManifestInService(
      String accountId, String orgId, String projectId, String serviceRef, String manifestPath);

  StoreDelegateConfig getStoreDelegateConfig(HelmChartManifestOutcome helmChartManifestOutcome, String accountId,
      String orgId, String projectId, String connectorId, String region, String bucketName, String folderPath);
}

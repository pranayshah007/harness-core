package io.harness.cdng.manifest.resources;

import io.harness.cdng.manifest.resources.dtos.HelmChartResponseDTO;
import io.harness.cdng.manifest.resources.dtos.HelmManifestInternalDTO;
import io.harness.cdng.manifest.yaml.HelmChartManifestOutcome;
import io.harness.delegate.beans.storeconfig.StoreDelegateConfig;

public interface HelmChartService {
  HelmChartResponseDTO getHelmChartVersionDetails(
      String accountId, String orgId, String projectId, String serviceRef, String manifestPath);

  HelmManifestInternalDTO locateManifestInService(
      String accountId, String orgId, String projectId, String serviceRef, String manifestPath);

  StoreDelegateConfig getStoreDelegateConfig(
      HelmChartManifestOutcome helmChartManifestOutcome, String accountId, String orgId, String projectId);
}

/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.helm;

import io.harness.artifactory.ArtifactoryConfigRequest;
import io.harness.delegate.task.manifests.request.ManifestCollectionParams;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.perpetualtask.manifest.ManifestRepositoryService;

import software.wings.beans.dto.HelmChart;
import software.wings.beans.settings.helm.HttpHelmRepoConfig;
import software.wings.helpers.ext.artifactory.ArtifactoryService;
import software.wings.helpers.ext.helm.request.ArtifactoryHelmTaskHelper;
import software.wings.helpers.ext.helm.request.HelmChartCollectionParams;
import software.wings.service.intfc.security.EncryptionService;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ArtifactoryHelmRepositoryService implements ManifestRepositoryService {
  private static final int maxVersions = 50;
  @Inject ArtifactoryService artifactoryService;
  @Inject EncryptionService encryptionService;
  @Inject ArtifactoryHelmTaskHelper artifactoryHelmTaskHelper;

  @Override
  public List<HelmChart> collectManifests(ManifestCollectionParams params) throws Exception {
    HelmChartCollectionParams helmChartCollectionParams = (HelmChartCollectionParams) params;
    HttpHelmRepoConfig helmRepoConfig =
        (HttpHelmRepoConfig) helmChartCollectionParams.getHelmChartConfigParams().getHelmRepoConfig();

    log.info("Collecting helm charts by name: {}; from artifactory for appManifestId: {}",
        helmChartCollectionParams.getHelmChartConfigParams().getChartName(),
        helmChartCollectionParams.getAppManifestId());

    encryptionService.decrypt(
        helmRepoConfig, helmChartCollectionParams.getHelmChartConfigParams().getEncryptedDataDetails(), false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
        helmRepoConfig, helmChartCollectionParams.getHelmChartConfigParams().getEncryptedDataDetails());

    ArtifactoryConfigRequest request =
        artifactoryHelmTaskHelper.getArtifactoryConfigRequestFromHelmRepoConfig(helmRepoConfig);

    String repoName = artifactoryHelmTaskHelper.getArtifactoryRepoNameFromHelmConfig(helmRepoConfig);

    List<HelmChart> helmCharts = artifactoryService.getHelmCharts(request, repoName,
        helmChartCollectionParams.getHelmChartConfigParams().getChartName(), maxVersions,
        helmChartCollectionParams.getHelmChartConfigParams().getChartVersion(), helmChartCollectionParams.isRegex());

    if (helmCharts == null) {
      return new ArrayList<>();
    }
    return helmCharts.stream()
        .map(helmChart
            -> HelmChart.builder()
                   .appId(helmChartCollectionParams.getAppId())
                   .accountId(helmChartCollectionParams.getAccountId())
                   .applicationManifestId(helmChartCollectionParams.getAppManifestId())
                   .serviceId(helmChartCollectionParams.getServiceId())
                   .name(helmChartCollectionParams.getHelmChartConfigParams().getChartName())
                   .version(helmChart.getVersion())
                   .displayName(helmChart.getDisplayName())
                   .appVersion(helmChart.getAppVersion())
                   .build())
        .collect(Collectors.toList());
  }

  // No cleanup action needed after collection
  @Override
  public void cleanup(ManifestCollectionParams params) throws Exception {}
}

/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.chartmuseum;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.chartmuseum.ChartmuseumClient;
import io.harness.chartmuseum.ChartmuseumClientFactory;
import io.harness.k8s.K8sGlobalConfigService;

import software.wings.beans.AwsConfig;
import software.wings.beans.GcpConfig;
import software.wings.beans.settings.helm.AmazonS3HelmRepoConfig;
import software.wings.beans.settings.helm.GCSHelmRepoConfig;
import software.wings.beans.settings.helm.HelmRepoConfig;
import software.wings.settings.SettingValue;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@OwnedBy(CDP)
@Singleton
public class CgChartmuseumClientFactory {
  @Inject private ChartmuseumClientFactory clientFactory;
  @Inject private K8sGlobalConfigService k8sGlobalConfigService;

  public ChartmuseumClient createClient(HelmRepoConfig helmRepoConfig, SettingValue connectorConfig,
      String resourceDirectory, String basePath, boolean useLatestChartMuseumVersion) {
    if (helmRepoConfig instanceof AmazonS3HelmRepoConfig) {
      AmazonS3HelmRepoConfig s3RepoConfig = (AmazonS3HelmRepoConfig) helmRepoConfig;
      AwsConfig awsConfig = (AwsConfig) connectorConfig;
      return clientFactory.s3(k8sGlobalConfigService.getChartMuseumPath(useLatestChartMuseumVersion),
          s3RepoConfig.getBucketName(), basePath, s3RepoConfig.getRegion(), awsConfig.isUseEc2IamCredentials(),
          awsConfig.getAccessKey(), awsConfig.getSecretKey(), awsConfig.isUseIRSA());
    } else if (helmRepoConfig instanceof GCSHelmRepoConfig) {
      GCSHelmRepoConfig gcsRepoConfig = (GCSHelmRepoConfig) helmRepoConfig;
      GcpConfig config = (GcpConfig) connectorConfig;
      return clientFactory.gcs(k8sGlobalConfigService.getChartMuseumPath(useLatestChartMuseumVersion),
          gcsRepoConfig.getBucketName(), basePath, config.getServiceAccountKeyFileContent(), resourceDirectory);
    }

    throw new UnsupportedOperationException(
        "Unhandled type of helm repo config. Type : " + helmRepoConfig.getSettingType());
  }
}

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.polling.manifest;

import static io.harness.delegate.beans.storeconfig.StoreDelegateConfigType.HTTP_HELM;
import static io.harness.delegate.task.k8s.ManifestType.HELM_CHART;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.storeconfig.GcsHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.HttpHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.OciHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.S3HelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.StoreDelegateConfig;
import io.harness.delegate.exception.ManifestCollectionException;
import io.harness.delegate.task.helm.HelmTaskHelperBase;
import io.harness.delegate.task.k8s.HelmChartManifestDelegateConfig;
import io.harness.delegate.task.k8s.ManifestDelegateConfig;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;

@OwnedBy(HarnessTeam.CDC)
@Singleton
public class HelmChartCollectionService implements ManifestCollectionService {
  private static final String MANIFEST_COLLECTION_DIR_BASE = "./manifest-collection/helm/";
  private static final long TIMEOUT_IN_MILLIS = 90L * 1000;

  @Inject private HelmTaskHelperBase helmTaskHelperBase;

  @Override
  public List<String> collectManifests(ManifestDelegateConfig params) {
    if (HELM_CHART.equals(params.getManifestType())) {
      try {
        HelmChartManifestDelegateConfig helmConfig = (HelmChartManifestDelegateConfig) params;
        String workingDirectory = getWorkingDirectory(helmConfig.getStoreDelegateConfig());
        helmTaskHelperBase.decryptEncryptedDetails(helmConfig);
        return helmTaskHelperBase.fetchChartVersions(helmConfig, TIMEOUT_IN_MILLIS, workingDirectory);
      } catch (Exception e) {
        throw new ManifestCollectionException("Exception while collecting manifests from repo: " + e.getMessage(), e);
      }

    } else {
      throw new ManifestCollectionException("Collection not yet implemented for given manifest type");
    }
  }

  @Override
  public void cleanup(ManifestDelegateConfig params) {
    if (HELM_CHART.equals(params.getManifestType())) {
      try {
        HelmChartManifestDelegateConfig helmConfig = (HelmChartManifestDelegateConfig) params;
        String workingDirectory = getWorkingDirectory(helmConfig.getStoreDelegateConfig());
        helmTaskHelperBase.cleanupAfterCollection(helmConfig, workingDirectory, TIMEOUT_IN_MILLIS);
      } catch (Exception e) {
        throw new ManifestCollectionException("Exception while collecting manifests from repo: " + e.getMessage(), e);
      }
    } else {
      throw new ManifestCollectionException("Collection not yet implemented for given manifest type");
    }
  }

  private String getWorkingDirectory(StoreDelegateConfig storeDelegateConfig) {
    switch (storeDelegateConfig.getType()) {
      case HTTP_HELM:
        HttpHelmStoreDelegateConfig helmStoreConfig = (HttpHelmStoreDelegateConfig) storeDelegateConfig;
        return MANIFEST_COLLECTION_DIR_BASE + HTTP_HELM.name() + "-" + helmStoreConfig.getRepoName();
      case OCI_HELM:
        OciHelmStoreDelegateConfig ociHelmStoreConfig = (OciHelmStoreDelegateConfig) storeDelegateConfig;
        return MANIFEST_COLLECTION_DIR_BASE + HTTP_HELM.name() + "-" + ociHelmStoreConfig.getRepoName();
      case S3_HELM:
        S3HelmStoreDelegateConfig s3StoreConfig = (S3HelmStoreDelegateConfig) storeDelegateConfig;
        return MANIFEST_COLLECTION_DIR_BASE + HTTP_HELM.name() + "-" + s3StoreConfig.getRepoName();
      case GCS_HELM:
        GcsHelmStoreDelegateConfig gcsStoreConfig = (GcsHelmStoreDelegateConfig) storeDelegateConfig;
        return MANIFEST_COLLECTION_DIR_BASE + HTTP_HELM.name() + "-" + gcsStoreConfig.getRepoName();
      default:
        throw new ManifestCollectionException("Manifest collection not supported for other helm repos");
    }
  }
}

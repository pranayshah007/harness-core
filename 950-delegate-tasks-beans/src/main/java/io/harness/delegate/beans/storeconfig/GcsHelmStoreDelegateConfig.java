/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.storeconfig;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class GcsHelmStoreDelegateConfig implements StoreDelegateConfig {
  String repoName;
  String repoDisplayName;
  String bucketName;
  String folderPath;
  boolean useLatestChartMuseumVersion;

  GcpConnectorDTO gcpConnector;
  List<EncryptedDataDetail> encryptedDataDetails;

  @Override
  public StoreDelegateConfigType getType() {
    return StoreDelegateConfigType.GCS_HELM;
  }

  @Override
  public String getRepoUrl() {
    return new StringBuilder("gs://").append(this.getBucketName()).append("/").append(this.getFolderPath()).toString();
  }
}

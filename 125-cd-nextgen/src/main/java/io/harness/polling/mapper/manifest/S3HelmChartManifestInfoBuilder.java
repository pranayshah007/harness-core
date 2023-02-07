/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.polling.mapper.manifest;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.yaml.S3StoreConfig;
import io.harness.k8s.model.HelmVersion;
import io.harness.pms.yaml.ParameterField;
import io.harness.polling.bean.PollingInfo;
import io.harness.polling.bean.manifest.HelmChartManifestInfo;
import io.harness.polling.contracts.PollingPayloadData;
import io.harness.polling.contracts.S3HelmPayload;
import io.harness.polling.mapper.PollingInfoBuilder;

@OwnedBy(HarnessTeam.CDC)
public class S3HelmChartManifestInfoBuilder implements PollingInfoBuilder {
  @Override
  public PollingInfo toPollingInfo(PollingPayloadData pollingPayloadData) {
    S3HelmPayload s3HelmPayload = pollingPayloadData.getS3HelmPayload();
    return HelmChartManifestInfo.builder()
        .store(S3StoreConfig.builder()
                   .connectorRef(ParameterField.<String>builder().value(pollingPayloadData.getConnectorRef()).build())
                   .bucketName(ParameterField.<String>builder().value(s3HelmPayload.getBucketName()).build())
                   .region(ParameterField.<String>builder().value(s3HelmPayload.getRegion()).build())
                   .folderPath(ParameterField.<String>builder().value(s3HelmPayload.getFolderPath()).build())
                   .build())
        .chartName(s3HelmPayload.getChartName())
        .helmVersion(HelmVersion.fromString(s3HelmPayload.getHelmVersion().name()))
        .build();
  }
}

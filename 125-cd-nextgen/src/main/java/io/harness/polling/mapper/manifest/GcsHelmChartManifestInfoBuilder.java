/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.polling.mapper.manifest;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.yaml.GcsStoreConfig;
import io.harness.k8s.model.HelmVersion;
import io.harness.pms.yaml.ParameterField;
import io.harness.polling.bean.PollingInfo;
import io.harness.polling.bean.manifest.HelmChartManifestInfo;
import io.harness.polling.contracts.GcsHelmPayload;
import io.harness.polling.contracts.PollingPayloadData;
import io.harness.polling.mapper.PollingInfoBuilder;

@OwnedBy(HarnessTeam.CDC)
public class GcsHelmChartManifestInfoBuilder implements PollingInfoBuilder {
  @Override
  public PollingInfo toPollingInfo(PollingPayloadData pollingPayloadData) {
    GcsHelmPayload gcsHelmPayload = pollingPayloadData.getGcsHelmPayload();
    return HelmChartManifestInfo.builder()
        .store(GcsStoreConfig.builder()
                   .connectorRef(ParameterField.<String>builder().value(pollingPayloadData.getConnectorRef()).build())
                   .bucketName(ParameterField.<String>builder().value(gcsHelmPayload.getBucketName()).build())
                   .folderPath(ParameterField.<String>builder().value(gcsHelmPayload.getFolderPath()).build())
                   .build())
        .chartName(gcsHelmPayload.getChartName())
        .helmVersion(HelmVersion.fromString(gcsHelmPayload.getHelmVersion().name()))
        .build();
  }
}

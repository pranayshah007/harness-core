/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.polling.mapper.manifest;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.yaml.HttpStoreConfig;
import io.harness.k8s.model.HelmVersion;
import io.harness.pms.yaml.ParameterField;
import io.harness.polling.bean.PollingInfo;
import io.harness.polling.bean.manifest.HelmChartManifestInfo;
import io.harness.polling.contracts.HttpHelmPayload;
import io.harness.polling.contracts.PollingPayloadData;
import io.harness.polling.mapper.PollingInfoBuilder;

@OwnedBy(HarnessTeam.CDC)
public class HttpHelmChartManifestInfoBuilder implements PollingInfoBuilder {
  @Override
  public PollingInfo toPollingInfo(PollingPayloadData pollingPayloadData) {
    HttpHelmPayload httpHelmPayload = pollingPayloadData.getHttpHelmPayload();
    return HelmChartManifestInfo.builder()
        .store(HttpStoreConfig.builder()
                   .connectorRef(ParameterField.<String>builder().value(pollingPayloadData.getConnectorRef()).build())
                   .build())
        .chartName(httpHelmPayload.getChartName())
        .helmVersion(HelmVersion.fromString(httpHelmPayload.getHelmVersion().name()))
        .build();
  }
}

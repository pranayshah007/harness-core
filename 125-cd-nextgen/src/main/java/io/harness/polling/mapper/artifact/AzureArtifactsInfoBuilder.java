/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.polling.mapper.artifact;

import io.harness.polling.bean.PollingInfo;
import io.harness.polling.bean.artifact.AzureArtifactsInfo;
import io.harness.polling.contracts.PollingPayloadData;
import io.harness.polling.mapper.PollingInfoBuilder;

public class AzureArtifactsInfoBuilder implements PollingInfoBuilder {
  @Override
  public PollingInfo toPollingInfo(PollingPayloadData pollingPayloadData) {
    return AzureArtifactsInfo.builder()
        .connectorRef(pollingPayloadData.getConnectorRef())
        .project(pollingPayloadData.getAzureArtifactsPayload().getProject())
        .feed(pollingPayloadData.getAzureArtifactsPayload().getFeed())
        .packageName(pollingPayloadData.getAzureArtifactsPayload().getPackageName())
        .packageType(pollingPayloadData.getAzureArtifactsPayload().getPackageType())
        .versionRegex(pollingPayloadData.getAzureArtifactsPayload().getVersionRegex())
        .version(pollingPayloadData.getAzureArtifactsPayload().getVersion())
        .build();
  }
}

/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.polling.mapper.artifact;

import io.harness.polling.bean.PollingInfo;
import io.harness.polling.bean.artifact.S3ArtifactInfo;
import io.harness.polling.contracts.PollingPayloadData;
import io.harness.polling.mapper.PollingInfoBuilder;

public class S3ArtifactInfoBuilder implements PollingInfoBuilder {
  @Override
  public PollingInfo toPollingInfo(PollingPayloadData pollingPayloadData) {
    return S3ArtifactInfo.builder()
        .connectorRef(pollingPayloadData.getConnectorRef())
        .bucketName(pollingPayloadData.getAmazonS3Payload().getBucketName())
        .filePathRegex(pollingPayloadData.getAmazonS3Payload().getFilePathRegex())
        .build();
  }
}

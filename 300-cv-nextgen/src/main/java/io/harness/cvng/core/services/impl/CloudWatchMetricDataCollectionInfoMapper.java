/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.cvng.beans.CloudWatchMetricDataCollectionInfo;
import io.harness.cvng.core.entities.CloudWatchMetricCVConfig;
import io.harness.cvng.core.services.api.MetricDataCollectionInfoMapper;

public class CloudWatchMetricDataCollectionInfoMapper
    extends MetricDataCollectionInfoMapper<CloudWatchMetricDataCollectionInfo, CloudWatchMetricCVConfig> {
  @Override
  protected CloudWatchMetricDataCollectionInfo toDataCollectionInfo(CloudWatchMetricCVConfig cvConfig) {
    CloudWatchMetricDataCollectionInfo cloudWatchMetricDataCollectionInfo =
        CloudWatchMetricDataCollectionInfo.builder()
            .region(cvConfig.getRegion())
            .metricPack(cvConfig.getMetricPack().toDTO())
            .build();
    cloudWatchMetricDataCollectionInfo.setDataCollectionDsl(cvConfig.getDataCollectionDsl());
    // TODO: Add mapper code
    return cloudWatchMetricDataCollectionInfo;
  }
}

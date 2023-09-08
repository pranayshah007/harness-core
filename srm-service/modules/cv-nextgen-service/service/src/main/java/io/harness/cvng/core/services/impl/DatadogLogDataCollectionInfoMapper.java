/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.cvng.beans.DatadogLogDataCollectionInfo;
import io.harness.cvng.beans.datadog.DatadogLogDefinition;
import io.harness.cvng.core.entities.DatadogLogCVConfig;
import io.harness.cvng.core.entities.LogCVConfig;
import io.harness.cvng.core.entities.NextGenLogCVConfig;
import io.harness.cvng.core.entities.VerificationTask.TaskType;
import io.harness.cvng.core.services.api.DataCollectionInfoMapper;

import java.util.List;

public class DatadogLogDataCollectionInfoMapper
    implements DataCollectionInfoMapper<DatadogLogDataCollectionInfo, LogCVConfig> {
  @Override
  public DatadogLogDataCollectionInfo toDataCollectionInfo(LogCVConfig cvConfig, TaskType taskType) {
    DatadogLogDataCollectionInfo datadogLogDataCollectionInfo;
    if (cvConfig instanceof DatadogLogCVConfig) {
      DatadogLogCVConfig datadogLogCVConfig = (DatadogLogCVConfig) cvConfig;
      DatadogLogDefinition definition =
          DatadogLogDefinition.builder()
              .name(datadogLogCVConfig.getQueryName())
              .query(datadogLogCVConfig.getQuery())
              .indexes(datadogLogCVConfig.getIndexes())
              .serviceInstanceIdentifier(datadogLogCVConfig.getServiceInstanceIdentifier())
              .build();
      datadogLogDataCollectionInfo = DatadogLogDataCollectionInfo.builder().logDefinition(definition).build();
    } else {
      NextGenLogCVConfig nextGenLogCVConfig = (NextGenLogCVConfig) cvConfig;
      DatadogLogDefinition definition =
          DatadogLogDefinition.builder()
              .name(nextGenLogCVConfig.getQueryName())
              .query(nextGenLogCVConfig.getQuery())
              .indexes(List.of(nextGenLogCVConfig.getQueryParams().getIndex())) // TODO FIX it has a list of indexes ?
              .serviceInstanceIdentifier(nextGenLogCVConfig.getQueryParams().getServiceInstanceField())
              .build();
      datadogLogDataCollectionInfo = DatadogLogDataCollectionInfo.builder().logDefinition(definition).build();
    }
    datadogLogDataCollectionInfo.setDataCollectionDsl(cvConfig.getDataCollectionDsl());
    return datadogLogDataCollectionInfo;
  }
}

/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.cvng.beans.AzureLogsDataCollectionInfo;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.core.entities.NextGenLogCVConfig;
import io.harness.cvng.core.entities.VerificationTask;
import io.harness.cvng.core.services.api.DataCollectionInfoMapper;

public class AzureLogsDataCollectionInfoMapper
    implements DataCollectionInfoMapper<AzureLogsDataCollectionInfo, NextGenLogCVConfig> {
  @Override
  public AzureLogsDataCollectionInfo toDataCollectionInfo(
      NextGenLogCVConfig cvConfig, VerificationTask.TaskType taskType) {
    AzureLogsDataCollectionInfo azureLogsDataCollectionInfo =
        AzureLogsDataCollectionInfo.builder()
            .serviceInstanceIdentifier(cvConfig.getQueryParams().getServiceInstanceField())
            .messageIdentifier(cvConfig.getQueryParams().getMessageIdentifier())
            .timeStampIdentifier(cvConfig.getQueryParams().getTimeStampIdentifier())
            .resourceId(cvConfig.getQueryParams().getIndex())
            .query(cvConfig.getQuery())
            .build();
    azureLogsDataCollectionInfo.setDataCollectionDsl(DataCollectionDSLFactory.readLogDSL(DataSourceType.AZURE_LOGS));
    return azureLogsDataCollectionInfo;
  }
}

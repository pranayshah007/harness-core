/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.SumologicLogDataCollectionInfo;
import io.harness.cvng.core.entities.NextGenLogCVConfig;
import io.harness.cvng.core.entities.VerificationTask;
import io.harness.cvng.core.services.api.DataCollectionInfoMapper;

public class SumologicLogDataCollectionInfoMapper
    implements DataCollectionInfoMapper<SumologicLogDataCollectionInfo, NextGenLogCVConfig> {
  @Override
  public SumologicLogDataCollectionInfo toDataCollectionInfo(
      NextGenLogCVConfig cvConfig, VerificationTask.TaskType taskType) {
    // TODO make it null safe
    SumologicLogDataCollectionInfo sumologicLogDataCollectionInfo =
        SumologicLogDataCollectionInfo.builder()
            .query(cvConfig.getQuery())
            .serviceInstanceIdentifier(cvConfig.getQueryParams().getServiceInstanceField())
            .build();
    sumologicLogDataCollectionInfo.setDataCollectionDsl(NextGenLogCVConfig.readLogDSL(DataSourceType.SUMOLOGIC_LOG));
    return sumologicLogDataCollectionInfo;
  }
}
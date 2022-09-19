/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.cvng.beans.SplunkDataCollectionInfo;
import io.harness.cvng.core.entities.SplunkCVConfig;
import io.harness.cvng.core.entities.VerificationTask.TaskType;
import io.harness.cvng.core.services.api.DataCollectionInfoMapper;

public class SplunkDataCollectionInfoMapper
    implements DataCollectionInfoMapper<SplunkDataCollectionInfo, SplunkCVConfig> {
  @Override
  public SplunkDataCollectionInfo toDataCollectionInfo(SplunkCVConfig cvConfig, TaskType taskType) {
    SplunkDataCollectionInfo splunkDataCollectionInfo =
        SplunkDataCollectionInfo.builder()
            .query(cvConfig.getQuery())
            .serviceInstanceIdentifier(cvConfig.getServiceInstanceIdentifier())
            .build();
    splunkDataCollectionInfo.setDataCollectionDsl(cvConfig.getDataCollectionDsl());
    return splunkDataCollectionInfo;
  }
}

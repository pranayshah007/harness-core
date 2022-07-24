/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.cvng.beans.CustomHealthLogDataCollectionInfo;
import io.harness.cvng.beans.customhealthlog.CustomHealthLogInfo;
import io.harness.cvng.core.beans.CustomHealthRequestDefinition;
import io.harness.cvng.core.entities.CustomHealthLogCVConfig;
import io.harness.cvng.core.entities.VerificationTask;
import io.harness.cvng.core.services.api.DataCollectionInfoMapper;

public class CustomHealthLogDataCollectionInfoMapper
    implements DataCollectionInfoMapper<CustomHealthLogDataCollectionInfo, CustomHealthLogCVConfig> {
  @Override
  public CustomHealthLogDataCollectionInfo toDataCollectionInfo(
      CustomHealthLogCVConfig cvConfig, VerificationTask.TaskType taskType) {
    CustomHealthRequestDefinition requestDefinition = cvConfig.getRequestDefinition();
    CustomHealthLogDataCollectionInfo logDataCollectionInfo =
        CustomHealthLogDataCollectionInfo.builder()
            .customHealthLogInfo(CustomHealthLogInfo.builder()
                                     .endTimeInfo(requestDefinition.getEndTimeInfo())
                                     .startTimeInfo(requestDefinition.getStartTimeInfo())
                                     .body(requestDefinition.getRequestBody())
                                     .method(requestDefinition.getMethod())
                                     .urlPath(requestDefinition.getUrlPath())
                                     .queryName(cvConfig.getQueryName())
                                     .timestampJsonPath(cvConfig.getTimestampJsonPath())
                                     .logMessageJsonPath(cvConfig.getLogMessageJsonPath())
                                     .serviceInstanceJsonPath(cvConfig.getServiceInstanceJsonPath())
                                     .build())
            .build();
    logDataCollectionInfo.setDataCollectionDsl(cvConfig.getDataCollectionDsl());
    return logDataCollectionInfo;
  }
}

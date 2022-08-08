/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.cvng.beans.ErrorTrackingDataCollectionInfo;
import io.harness.cvng.core.entities.ErrorTrackingCVConfig;
import io.harness.cvng.core.entities.VerificationTask;
import io.harness.cvng.core.services.api.DataCollectionInfoMapper;

public class ErrorTrackingDataCollectionInfoMapper
    implements DataCollectionInfoMapper<ErrorTrackingDataCollectionInfo, ErrorTrackingCVConfig> {
  @Override
  public ErrorTrackingDataCollectionInfo toDataCollectionInfo(
      ErrorTrackingCVConfig cvConfig, VerificationTask.TaskType taskType) {
    ErrorTrackingDataCollectionInfo errorTrackingDataCollectionInfo =
        ErrorTrackingDataCollectionInfo.builder()
            .accountId(cvConfig.getAccountId())
            .orgId(cvConfig.getOrgIdentifier())
            .projectId(cvConfig.getProjectIdentifier())
            // TODO: figure out how to send service and env id.
            .serviceId(cvConfig.getServiceIdentifier())
            .environmentId(cvConfig.getEnvIdentifier())
            .build();
    errorTrackingDataCollectionInfo.setDataCollectionDsl(cvConfig.getDataCollectionDsl());
    errorTrackingDataCollectionInfo.setHostCollectionDSL(cvConfig.getHostCollectionDSL());
    return errorTrackingDataCollectionInfo;
  }
}

/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.cvng.beans.ELKDataCollectionInfo;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.ELKCVConfig;
import io.harness.cvng.core.entities.NextGenLogCVConfig;
import io.harness.cvng.core.entities.VerificationTask;
import io.harness.cvng.core.services.api.DataCollectionInfoMapper;

public class ELKDataCollectionInfoMapper implements DataCollectionInfoMapper<ELKDataCollectionInfo, CVConfig> {
  @Override
  public ELKDataCollectionInfo toDataCollectionInfo(CVConfig cvConfigBase, VerificationTask.TaskType taskType) {
    if (cvConfigBase instanceof ELKCVConfig) {
      ELKCVConfig cvConfig = (ELKCVConfig) cvConfigBase;
      ELKDataCollectionInfo elkDataCollectionInfo =
          ELKDataCollectionInfo.builder()
              .index(cvConfig.getIndex())
              .query(cvConfig.getQuery())
              .serviceInstanceIdentifier(cvConfig.getServiceInstanceIdentifier())
              .timeStampIdentifier(cvConfig.getTimeStampIdentifier())
              .timeStampFormat(cvConfig.getTimeStampFormat())
              .messageIdentifier(cvConfig.getMessageIdentifier())
              .build();
      elkDataCollectionInfo.setDataCollectionDsl(cvConfig.getDataCollectionDsl());
      return elkDataCollectionInfo;
    } else if (cvConfigBase instanceof NextGenLogCVConfig) {
      NextGenLogCVConfig cvConfig = (NextGenLogCVConfig) cvConfigBase;
      ELKDataCollectionInfo elkDataCollectionInfo =
          ELKDataCollectionInfo.builder()
              .index(cvConfig.getQueryParams().getIndex())
              .query(cvConfig.getQuery())
              .serviceInstanceIdentifier(cvConfig.getQueryParams().getServiceInstanceField())
              .timeStampIdentifier(cvConfig.getQueryParams().getTimeStampIdentifier())
              .timeStampFormat(cvConfig.getQueryParams().getTimeStampFormat())
              .messageIdentifier(cvConfig.getQueryParams().getMessageIdentifier())
              .build();
      elkDataCollectionInfo.setDataCollectionDsl(cvConfig.getDataCollectionDsl());
      return elkDataCollectionInfo;
    }
    throw new RuntimeException("Cannot convert CVConfig " + cvConfigBase);
  }
}

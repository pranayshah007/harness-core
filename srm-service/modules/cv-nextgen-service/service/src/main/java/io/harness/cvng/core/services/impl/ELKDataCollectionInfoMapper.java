/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.cvng.beans.ELKDataCollectionInfo;
import io.harness.cvng.core.entities.ELKCVConfig;
import io.harness.cvng.core.entities.VerificationTask;
import io.harness.cvng.core.services.api.DataCollectionInfoMapper;

public class ELKDataCollectionInfoMapper implements DataCollectionInfoMapper<ELKDataCollectionInfo, ELKCVConfig> {
  @Override
  public ELKDataCollectionInfo toDataCollectionInfo(ELKCVConfig cvConfig, VerificationTask.TaskType taskType) {
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
  }
}

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.api;

import io.harness.cvng.beans.DataCollectionInfo;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.VerificationTask.TaskType;

import java.util.List;

public interface DataCollectionInfoMapper<R extends DataCollectionInfo, T extends CVConfig> {
  R toDataCollectionInfo(T cvConfig, TaskType taskType);

  default R toDeploymentDataCollectionInfo(T cvConfig, List<String> serviceInstances) {
    R dataCollectionInfo = toDataCollectionInfo(cvConfig, TaskType.DEPLOYMENT);
    dataCollectionInfo.setServiceInstances(serviceInstances);
    return dataCollectionInfo;
  }

  default void postProcessDataCollectionInfo(R dataCollectionInfo, T cvConfig, TaskType taskType) {}
}

/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl.mapper;

import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.GrafanaLokiLogDataCollectionInfo;
import io.harness.cvng.core.entities.NextGenLogCVConfig;
import io.harness.cvng.core.entities.VerificationTask;
import io.harness.cvng.core.services.api.DataCollectionInfoMapper;
import io.harness.cvng.core.services.impl.DataCollectionDSLFactory;
import io.harness.cvng.core.services.impl.healthSource.GrafanaLokiLogNextGenHealthSourceHelper;

public class GrafanaLokiLogDataCollectionInfoMapper
    implements DataCollectionInfoMapper<GrafanaLokiLogDataCollectionInfo, NextGenLogCVConfig> {
  @Override
  public GrafanaLokiLogDataCollectionInfo toDataCollectionInfo(
      NextGenLogCVConfig cvConfig, VerificationTask.TaskType taskType) {
    GrafanaLokiLogDataCollectionInfo grafanaLokiLogDataCollectionInfo =
        GrafanaLokiLogDataCollectionInfo.builder()
            .urlEncodedQuery(GrafanaLokiLogNextGenHealthSourceHelper.encodeValue(cvConfig.getQuery()))
            .serviceInstanceIdentifier(cvConfig.getQueryParams().getServiceInstanceField())
            .build();
    grafanaLokiLogDataCollectionInfo.setDataCollectionDsl(
        DataCollectionDSLFactory.readDSL(DataSourceType.GRAFANA_LOKI_LOGS).getActualDataCollectionDSL());
    return grafanaLokiLogDataCollectionInfo;
  }
}
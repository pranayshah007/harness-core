/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.scorecard.datapoints.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.spec.server.idp.v1.model.DataPoint;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.IDP)
@UtilityClass
public class DataPointMapper {
  public DataPoint toDto(DataPointEntity dataPointEntity) {
    DataPoint dataPoint = new DataPoint();
    dataPoint.setName(dataPointEntity.getName());
    dataPoint.setDescription(dataPointEntity.getDescription());
    dataPoint.setType(dataPointEntity.getType().toString());
    dataPoint.setIsConditional(dataPointEntity.isConditional());
    dataPoint.setDescription(dataPointEntity.getConditionalInputValueDescription());
    return dataPoint;
  }
}

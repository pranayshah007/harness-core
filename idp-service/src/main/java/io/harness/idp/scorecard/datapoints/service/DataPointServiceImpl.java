/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.scorecard.datapoints.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datapoints.mappers.DataPointMapper;
import io.harness.idp.scorecard.datapoints.repositories.DataPointsRepository;
import io.harness.spec.server.idp.v1.model.DataPoint;

import java.util.List;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @com.google.inject.Inject }))
public class DataPointServiceImpl implements DataPointService {
  DataPointsRepository dataPointsRepository;

  @Override
  public List<DataPoint> getAllDataPointsDetailsForAccountAndDataSource(
      String accountIdentifier, String dataSourceIdentifier) {
    List<DataPointEntity> dataPointEntities =
        dataPointsRepository.findAllByAccountIdentifierAndDataSourceIdentifier(accountIdentifier, dataSourceIdentifier);
    return dataPointEntities.stream()
        .map(dataPointEntity -> DataPointMapper.toDto(dataPointEntity))
        .collect(Collectors.toList());
  }
}

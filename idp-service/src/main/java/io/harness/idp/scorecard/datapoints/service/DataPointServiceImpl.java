/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.service;

import static io.harness.idp.common.CommonUtils.addGlobalAccountIdentifierAlong;
import static io.harness.idp.common.Constants.DOT_SEPARATOR;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datapoints.mappers.DataPointMapper;
import io.harness.idp.scorecard.datapoints.repositories.DataPointsRepository;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;
import io.harness.spec.server.idp.v1.model.DataPoint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @com.google.inject.Inject }))
@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class DataPointServiceImpl implements DataPointService {
  DataPointsRepository dataPointsRepository;

  @Override
  public List<DataPoint> getAllDataPointsDetailsForAccountAndDataSource(
      String accountIdentifier, String dataSourceIdentifier) {
    List<DataPointEntity> dataPointEntities = dataPointsRepository.findAllByAccountIdentifierInAndDataSourceIdentifier(
        addGlobalAccountIdentifierAlong(accountIdentifier), dataSourceIdentifier);
    return dataPointEntities.stream().map(DataPointMapper::toDto).collect(Collectors.toList());
  }

  @Override
  public Map<String, List<DataPointEntity>> getDslDataPointsInfo(
      String accountIdentifier, List<String> identifiers, String dataSourceIdentifier) {
    List<DataPointEntity> dataPoints =
        dataPointsRepository.findByAccountIdentifierInAndDataSourceIdentifierAndIdentifierIn(
            addGlobalAccountIdentifierAlong(accountIdentifier), dataSourceIdentifier, identifiers);
    Map<String, List<DataPointEntity>> dslDataPointsInfo = new HashMap<>();
    for (DataPointEntity dataPoint : dataPoints) {
      List<DataPointEntity> dslDataPoints =
          dslDataPointsInfo.getOrDefault(dataPoint.getDataSourceLocationIdentifier(), new ArrayList<>());
      dslDataPoints.add(dataPoint);
      dslDataPointsInfo.put(dataPoint.getDataSourceLocationIdentifier(), dslDataPoints);
    }
    return dslDataPointsInfo;
  }

  @Override
  public List<DataPointEntity> getAllDataPointsForAccount(String accountIdentifier) {
    return dataPointsRepository.findAllByAccountIdentifierIn(addGlobalAccountIdentifierAlong(accountIdentifier));
  }

  @Override
  public Map<String, DataPoint> getDataPointsMap(String accountIdentifier) {
    Map<String, DataPoint> dataPointMap = new HashMap<>();
    List<DataPointEntity> dataPointsInAccount = getAllDataPointsForAccount(accountIdentifier);
    for (DataPointEntity dataPointEntity : dataPointsInAccount) {
      String key = dataPointEntity.getDataSourceIdentifier() + DOT_SEPARATOR + dataPointEntity.getIdentifier();
      dataPointMap.put(key, DataPointMapper.toDto(dataPointEntity));
    }
    return dataPointMap;
  }

  @Override
  public Map<String, List<DataFetchDTO>> getDslDataPointsInfo(
      String accountIdentifier, String dataSourceIdentifier, List<DataFetchDTO> dataFetchDTOS) {
    Set<String> identifiers = dataFetchDTOS.stream()
                                  .map(dataFetchDTO -> dataFetchDTO.getDataPoint().getIdentifier())
                                  .collect(Collectors.toSet());
    List<DataPointEntity> dataPoints =
        dataPointsRepository.findByAccountIdentifierInAndDataSourceIdentifierAndIdentifierIn(
            addGlobalAccountIdentifierAlong(accountIdentifier), dataSourceIdentifier, new ArrayList<>(identifiers));
    Map<String, DataPointEntity> dataPointsMap =
        dataPoints.stream().collect(Collectors.toMap(DataPointEntity::getIdentifier, Function.identity()));

    Map<String, List<DataFetchDTO>> dslDataPointsInfo = new HashMap<>();
    for (DataFetchDTO dataFetchDTO : dataFetchDTOS) {
      String dataPointIdentifier = dataFetchDTO.getDataPoint().getIdentifier();
      DataPointEntity dataPoint = dataPointsMap.get(dataPointIdentifier);
      dataFetchDTO.setDataPoint(dataPoint);
      if (!dslDataPointsInfo.containsKey(dataPoint.getDataSourceLocationIdentifier())) {
        dslDataPointsInfo.put(dataPoint.getDataSourceLocationIdentifier(), new ArrayList<>());
      }
      dslDataPointsInfo.get(dataPoint.getDataSourceLocationIdentifier()).add(dataFetchDTO);
    }
    return dslDataPointsInfo;
  }

  @Override
  public DataPointEntity getDataPoint(
      String accountIdentifier, String dataSourceIdentifier, String dataPointIdentifier) {
    Optional<DataPointEntity> datapointOpt =
        dataPointsRepository.findByAccountIdentifierInAndDataSourceIdentifierAndIdentifier(
            addGlobalAccountIdentifierAlong(accountIdentifier), dataSourceIdentifier, dataPointIdentifier);
    if (datapointOpt.isEmpty()) {
      throw new InvalidRequestException(
          String.format("Data point configured is not found for identifier %s and datasource identifier %s",
              dataSourceIdentifier, dataPointIdentifier));
    }
    return datapointOpt.get();
  }
}

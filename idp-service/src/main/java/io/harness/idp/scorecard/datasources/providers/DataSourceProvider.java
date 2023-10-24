/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasources.providers;

import static io.harness.idp.common.Constants.LOCAL_ENV;
import static io.harness.idp.common.Constants.LOCAL_HOST;
import static io.harness.idp.common.Constants.PRE_QA_ENV;
import static io.harness.idp.common.Constants.PRE_QA_HOST;
import static io.harness.idp.common.Constants.PROD_HOST;
import static io.harness.idp.common.Constants.QA_ENV;
import static io.harness.idp.common.Constants.QA_HOST;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.backstagebeans.BackstageCatalogEntity;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datapoints.parser.DataPointParser;
import io.harness.idp.scorecard.datapoints.parser.DataPointParserFactory;
import io.harness.idp.scorecard.datapoints.service.DataPointService;
import io.harness.idp.scorecard.datasourcelocations.entity.DataSourceLocationEntity;
import io.harness.idp.scorecard.datasourcelocations.locations.DataSourceLocation;
import io.harness.idp.scorecard.datasourcelocations.locations.DataSourceLocationFactory;
import io.harness.idp.scorecard.datasourcelocations.repositories.DataSourceLocationRepository;
import io.harness.spec.server.idp.v1.model.InputValue;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.UnsupportedEncodingException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.util.Pair;

@Data
@OwnedBy(HarnessTeam.IDP)
@Slf4j
public abstract class DataSourceProvider {
  public static final String HOST = "{HOST}";
  private String identifier;

  DataPointService dataPointService;
  DataSourceLocationFactory dataSourceLocationFactory;
  DataSourceLocationRepository dataSourceLocationRepository;
  DataPointParserFactory dataPointParserFactory;

  protected DataSourceProvider(String identifier, DataPointService dataPointService,
      DataSourceLocationFactory dataSourceLocationFactory, DataSourceLocationRepository dataSourceLocationRepository,
      DataPointParserFactory dataPointParserFactory) {
    this.identifier = identifier;
    this.dataPointService = dataPointService;
    this.dataSourceLocationFactory = dataSourceLocationFactory;
    this.dataSourceLocationRepository = dataSourceLocationRepository;
    this.dataPointParserFactory = dataPointParserFactory;
  }

  public abstract Map<String, Map<String, Object>> fetchData(String accountIdentifier, BackstageCatalogEntity entity,
      List<Pair<String, List<InputValue>>> dataPointsAndInputValues, String configs)
      throws UnsupportedEncodingException, JsonProcessingException, NoSuchAlgorithmException, KeyManagementException;

  protected abstract Map<String, String> getAuthHeaders(String accountIdentifier, String configs);

  protected Map<String, Map<String, Object>> processOut(String accountIdentifier,
      BackstageCatalogEntity backstageCatalogEntity, List<Pair<String, List<InputValue>>> dataPointIdsAndInputValues,
      Map<String, String> replaceableHeaders, Map<String, String> possibleReplaceableRequestBodyPairs,
      Map<String, String> possibleReplaceableUrlPairs) throws NoSuchAlgorithmException, KeyManagementException {
    Map<String, List<Pair<DataPointEntity, List<InputValue>>>> dataToFetchWithInputValues =
        dataPointService.getDslDataPointsInfo(accountIdentifier, this.getIdentifier(), dataPointIdsAndInputValues);

    Map<String, Map<String, Object>> aggregatedData = new HashMap<>();

    for (Map.Entry<String, List<Pair<DataPointEntity, List<InputValue>>>> entry :
        dataToFetchWithInputValues.entrySet()) {
      String dslIdentifier = entry.getKey();
      List<Pair<DataPointEntity, List<InputValue>>> dataPointsAndInputValues = entry.getValue();

      DataSourceLocation dataSourceLocation = dataSourceLocationFactory.getDataSourceLocation(dslIdentifier);
      DataSourceLocationEntity dataSourceLocationEntity = dataSourceLocationRepository.findByIdentifier(dslIdentifier);
      Map<String, Object> response = dataSourceLocation.fetchData(accountIdentifier, backstageCatalogEntity,
          dataSourceLocationEntity, dataPointsAndInputValues, replaceableHeaders, possibleReplaceableRequestBodyPairs,
          possibleReplaceableUrlPairs);
      log.info("Response for DSL in Process out - dsl Identifier - {} dataToFetchWithInputValues - {} Response - {} ",
          dslIdentifier, dataToFetchWithInputValues, response);

      parseResponseAgainstDataPoint(dataPointsAndInputValues, response, aggregatedData);
    }
    log.info("Aggregated data for data for DataPoints - {}, aggregated data - {}", dataPointIdsAndInputValues,
        aggregatedData);

    return aggregatedData;
  }

  private void parseResponseAgainstDataPoint(List<Pair<DataPointEntity, List<InputValue>>> dataPointsAndInputValues,
      Map<String, Object> response, Map<String, Map<String, Object>> aggregatedData) {
    Map<String, Object> dataPointValues = new HashMap<>();
    for (Pair<DataPointEntity, List<InputValue>> dataPointsAndInputValue : dataPointsAndInputValues) {
      DataPointEntity dataPointEntity = dataPointsAndInputValue.getFirst();
      List<InputValue> inputValues = dataPointsAndInputValue.getSecond();
      Object values;
      DataPointParser dataPointParser = dataPointParserFactory.getParser(dataPointEntity.getIdentifier());
      values = dataPointParser.parseDataPoint(response, dataPointEntity, inputValues);
      if (values != null) {
        dataPointValues.put(dataPointEntity.getIdentifier(), values);
      }
    }

    Map<String, Object> providerData = aggregatedData.getOrDefault(getIdentifier(), new HashMap<>());
    providerData.putAll(dataPointValues);
    aggregatedData.put(getIdentifier(), providerData);
  }

  public Map<String, String> prepareUrlReplaceablePairs(String env) {
    Map<String, String> possibleReplaceableUrlPairs = new HashMap<>();
    switch (env) {
      case QA_ENV:
        possibleReplaceableUrlPairs.put(HOST, QA_HOST);
        break;
      case PRE_QA_ENV:
        possibleReplaceableUrlPairs.put(HOST, PRE_QA_HOST);
        break;
      case LOCAL_ENV:
        possibleReplaceableUrlPairs.put(HOST, LOCAL_HOST);
        break;
      default:
        possibleReplaceableUrlPairs.put(HOST, PROD_HOST);
    }
    return possibleReplaceableUrlPairs;
  }
}

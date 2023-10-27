/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasources.providers;

import static io.harness.idp.common.CommonUtils.addGlobalAccountIdentifierAlong;
import static io.harness.idp.common.Constants.LOCAL_ENV;
import static io.harness.idp.common.Constants.LOCAL_HOST;
import static io.harness.idp.common.Constants.PRE_QA_ENV;
import static io.harness.idp.common.Constants.PRE_QA_HOST;
import static io.harness.idp.common.Constants.PROD_HOST;
import static io.harness.idp.common.Constants.QA_ENV;
import static io.harness.idp.common.Constants.QA_HOST;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.backstagebeans.BackstageCatalogEntity;
import io.harness.idp.scorecard.common.beans.DataSourceConfig;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datapoints.parser.DataPointParser;
import io.harness.idp.scorecard.datapoints.parser.DataPointParserFactory;
import io.harness.idp.scorecard.datapoints.service.DataPointService;
import io.harness.idp.scorecard.datasourcelocations.entity.DataSourceLocationEntity;
import io.harness.idp.scorecard.datasourcelocations.locations.DataSourceLocation;
import io.harness.idp.scorecard.datasourcelocations.locations.DataSourceLocationFactory;
import io.harness.idp.scorecard.datasourcelocations.repositories.DataSourceLocationRepository;
import io.harness.idp.scorecard.datasources.entity.DataSourceEntity;
import io.harness.idp.scorecard.datasources.repositories.DataSourceRepository;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.UnsupportedEncodingException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

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
  DataSourceRepository dataSourceRepository;

  protected DataSourceProvider(String identifier, DataPointService dataPointService,
      DataSourceLocationFactory dataSourceLocationFactory, DataSourceLocationRepository dataSourceLocationRepository,
      DataPointParserFactory dataPointParserFactory, DataSourceRepository dataSourceRepository) {
    this.identifier = identifier;
    this.dataPointService = dataPointService;
    this.dataSourceLocationFactory = dataSourceLocationFactory;
    this.dataSourceLocationRepository = dataSourceLocationRepository;
    this.dataPointParserFactory = dataPointParserFactory;
    this.dataSourceRepository = dataSourceRepository;
  }

  public abstract Map<String, Map<String, Object>> fetchData(String accountIdentifier, BackstageCatalogEntity entity,
      Map<String, Set<String>> dataPointsAndInputValues, String configs)
      throws UnsupportedEncodingException, JsonProcessingException, NoSuchAlgorithmException, KeyManagementException;

  protected abstract Map<String, String> getAuthHeaders(String accountIdentifier, String configs);

  protected Map<String, Map<String, Object>> processOut(String accountIdentifier, String identifier,
      BackstageCatalogEntity backstageCatalogEntity, Map<String, String> replaceableHeaders,
      Map<String, String> possibleReplaceableRequestBodyPairs, Map<String, String> possibleReplaceableUrlPairs,
      Map<String, Set<String>> dataPointsAndInputValues) throws NoSuchAlgorithmException, KeyManagementException {
    Optional<DataSourceEntity> dataSourceEntityOpt = dataSourceRepository.findByAccountIdentifierInAndIdentifier(
        addGlobalAccountIdentifierAlong(accountIdentifier), identifier);
    if (dataSourceEntityOpt.isEmpty()) {
      throw new InvalidRequestException(
          String.format("Data source %s not found in account %s", identifier, accountIdentifier));
    }
    DataSourceEntity dataSourceEntity = dataSourceEntityOpt.get();
    DataSourceConfig config =
        this.getDataSourceConfig(dataSourceEntity, replaceableHeaders, possibleReplaceableUrlPairs);

    Set<String> dataPointIdentifiers = dataPointsAndInputValues.keySet();
    Map<String, List<DataPointEntity>> dataToFetch = dataPointService.getDslDataPointsInfo(
        accountIdentifier, new ArrayList<>(dataPointIdentifiers), this.getIdentifier());

    Map<String, Map<String, Object>> aggregatedData = new HashMap<>();

    for (String dslIdentifier : dataToFetch.keySet()) {
      Map<DataPointEntity, Set<String>> dataToFetchWithInputValues =
          prepareDataToFetch(dataToFetch, dslIdentifier, dataPointsAndInputValues);

      DataSourceLocation dataSourceLocation = dataSourceLocationFactory.getDataSourceLocation(dslIdentifier);
      DataSourceLocationEntity dataSourceLocationEntity = dataSourceLocationRepository.findByIdentifier(dslIdentifier);
      Map<String, Object> response = dataSourceLocation.fetchData(accountIdentifier, backstageCatalogEntity,
          dataSourceLocationEntity, dataToFetchWithInputValues, replaceableHeaders, possibleReplaceableRequestBodyPairs,
          possibleReplaceableUrlPairs, config);
      log.info("Response for DSL in Process out - dsl Identifier - {} dataToFetchWithInputValues - {} Response - {} ",
          dslIdentifier, dataToFetchWithInputValues, response);

      parseResponseAgainstDataPoint(dataToFetchWithInputValues, response, aggregatedData);
    }
    log.info(
        "Aggregated data for data for DataPoints - {}, aggregated data - {}", dataPointsAndInputValues, aggregatedData);

    return aggregatedData;
  }

  protected abstract DataSourceConfig getDataSourceConfig(DataSourceEntity dataSourceEntity,
      Map<String, String> replaceableHeaders, Map<String, String> possibleReplaceableUrlPairs);

  private Map<DataPointEntity, Set<String>> prepareDataToFetch(Map<String, List<DataPointEntity>> dataToFetch,
      String dslIdentifier, Map<String, Set<String>> dataPointsAndInputValues) {
    Map<DataPointEntity, Set<String>> dataToFetchWithInputValues = new HashMap<>();
    dataToFetch.get(dslIdentifier)
        .forEach(dataPointEntity
            -> dataToFetchWithInputValues.put(
                dataPointEntity, dataPointsAndInputValues.get(dataPointEntity.getIdentifier())));
    return dataToFetchWithInputValues;
  }

  private void parseResponseAgainstDataPoint(Map<DataPointEntity, Set<String>> dataToFetchWithInputValues,
      Map<String, Object> response, Map<String, Map<String, Object>> aggregatedData) {
    Map<String, Object> dataPointValues = new HashMap<>();
    for (Map.Entry<DataPointEntity, Set<String>> entry : dataToFetchWithInputValues.entrySet()) {
      DataPointEntity dataPointEntity = entry.getKey();

      Object values;
      Set<String> inputValues = entry.getValue();
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

  String replaceUrlsPlaceholdersIfAny(String url, Map<String, String> replaceableUrls) {
    for (Map.Entry<String, String> entry : replaceableUrls.entrySet()) {
      url = url.replace(entry.getKey(), entry.getValue());
    }
    return url;
  }

  void matchAndReplaceHeaders(Map<String, String> headers, Map<String, String> replaceableHeaders) {
    headers.forEach((k, v) -> {
      if (replaceableHeaders.containsKey(k)) {
        headers.put(k, replaceableHeaders.get(k));
      }
    });
  }
}

/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasources.providers;

import static io.harness.idp.common.CommonUtils.addGlobalAccountIdentifierAlong;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.backstagebeans.BackstageCatalogEntity;
import io.harness.idp.scorecard.common.beans.DataSourceConfig;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datapoints.parser.DataPointParser;
import io.harness.idp.scorecard.datapoints.parser.factory.DataPointParserFactory;
import io.harness.idp.scorecard.datapoints.service.DataPointService;
import io.harness.idp.scorecard.datasourcelocations.entity.DataSourceLocationEntity;
import io.harness.idp.scorecard.datasourcelocations.locations.DataSourceLocation;
import io.harness.idp.scorecard.datasourcelocations.locations.DataSourceLocationFactory;
import io.harness.idp.scorecard.datasourcelocations.locations.DataSourceLocationLoop;
import io.harness.idp.scorecard.datasourcelocations.repositories.DataSourceLocationRepository;
import io.harness.idp.scorecard.datasources.entity.DataSourceEntity;
import io.harness.idp.scorecard.datasources.repositories.DataSourceRepository;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
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
      List<DataFetchDTO> dataPointsAndInputValues, String configs);

  protected Map<String, String> prepareUrlReplaceablePairs(String... keysValues) {
    Map<String, String> possibleReplaceableUrlPairs = new HashMap<>();
    for (int i = 0; i < keysValues.length; i = i + 2) {
      possibleReplaceableUrlPairs.put(keysValues[i], keysValues[i + 1]);
    }
    return possibleReplaceableUrlPairs;
  }

  protected abstract Map<String, String> getAuthHeaders(String accountIdentifier, String configs);

  protected abstract DataSourceConfig getDataSourceConfig(DataSourceEntity dataSourceEntity,
      Map<String, String> possibleReplaceableUrlPairs, Map<String, String> replaceableHeaders);

  protected Map<String, Map<String, Object>> processOut(String accountIdentifier, String identifier,
      BackstageCatalogEntity backstageCatalogEntity, Map<String, String> replaceableHeaders,
      Map<String, String> possibleReplaceableRequestBodyPairs, Map<String, String> possibleReplaceableUrlPairs,
      List<DataFetchDTO> dataToFetch) {
    Map<String, List<DataFetchDTO>> dataToFetchByDsl =
        dataPointService.getDslDataPointsInfo(accountIdentifier, this.getIdentifier(), dataToFetch);
    Optional<DataSourceEntity> dataSourceEntityOpt = dataSourceRepository.findByAccountIdentifierInAndIdentifier(
        addGlobalAccountIdentifierAlong(accountIdentifier), identifier);
    if (dataSourceEntityOpt.isEmpty()) {
      throw new InvalidRequestException(
          String.format("Data source %s not found in account %s", identifier, accountIdentifier));
    }
    DataSourceEntity dataSourceEntity = dataSourceEntityOpt.get();
    DataSourceConfig config =
        this.getDataSourceConfig(dataSourceEntity, possibleReplaceableUrlPairs, replaceableHeaders);

    Map<String, Map<String, Object>> aggregatedData = new HashMap<>();

    for (Map.Entry<String, List<DataFetchDTO>> entry : dataToFetchByDsl.entrySet()) {
      String dslIdentifier = entry.getKey();
      List<DataFetchDTO> dataToFetchForDsl = entry.getValue();

      Set<String> ruleIdentifiers =
          dataToFetchForDsl.stream().map(DataFetchDTO::getRuleIdentifier).collect(Collectors.toSet());
      log.debug(
          "Fetching data for entity {} and DTOs {}", backstageCatalogEntity.getMetadata().getUid(), dataToFetchForDsl);

      DataSourceLocation dataSourceLocation = dataSourceLocationFactory.getDataSourceLocation(dslIdentifier);
      DataSourceLocationEntity dataSourceLocationEntity = dataSourceLocationRepository.findByIdentifier(dslIdentifier);

      if (dataSourceLocation instanceof DataSourceLocationLoop) {
        try {
          Map<String, Object> response = dataSourceLocation.fetchData(accountIdentifier, backstageCatalogEntity,
              dataSourceLocationEntity, dataToFetchForDsl, replaceableHeaders, possibleReplaceableRequestBodyPairs,
              possibleReplaceableUrlPairs, config);
          log.info("Response for DSL in Process out - dsl Identifier - {} rule identifiers - {} Response - {} ",
              dslIdentifier, ruleIdentifiers, response);

          for (DataFetchDTO dataFetchDTO : dataToFetchForDsl) {
            parseResponseAgainstDataPoint(dataFetchDTO, response, aggregatedData);
          }
        } catch (Exception e) {
          log.warn(
              "Could not fetch data for dsl identifier - {} rule identifiers - {}", dslIdentifier, ruleIdentifiers, e);
        }
      } else {
        for (DataFetchDTO dataFetchDTO : dataToFetchForDsl) {
          try {
            Map<String, Object> response = dataSourceLocation.fetchData(accountIdentifier, backstageCatalogEntity,
                dataSourceLocationEntity, Collections.singletonList(dataFetchDTO), replaceableHeaders,
                possibleReplaceableRequestBodyPairs, possibleReplaceableUrlPairs, config);
            log.info("Response for DSL in Process out - dsl Identifier - {} rule identifiers - {} Response - {} ",
                dslIdentifier, ruleIdentifiers, response);

            parseResponseAgainstDataPoint(dataFetchDTO, response, aggregatedData);
          } catch (Exception e) {
            log.warn("Could not fetch data for dsl identifier - {} rule identifiers - {}", dslIdentifier,
                ruleIdentifiers, e);
          }
        }
      }
    }
    log.info(
        "Aggregated data for entity with uuid {} : {}", backstageCatalogEntity.getMetadata().getUid(), aggregatedData);

    return aggregatedData;
  }

  String replaceUrlsPlaceholdersIfAny(String url, Map<String, String> possibleReplaceableUrlPairs) {
    for (Map.Entry<String, String> entry : possibleReplaceableUrlPairs.entrySet()) {
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

  private void parseResponseAgainstDataPoint(
      DataFetchDTO dataFetchDTO, Map<String, Object> response, Map<String, Map<String, Object>> aggregatedData) {
    Map<String, Object> providerData = aggregatedData.getOrDefault(getIdentifier(), new HashMap<>());

    DataPointEntity dataPointEntity = dataFetchDTO.getDataPoint();
    Object values;
    DataPointParser dataPointParser = dataPointParserFactory.getParser(dataPointEntity.getIdentifier());
    values = dataPointParser.parseDataPoint(response, dataFetchDTO);
    if (values != null) {
      providerData.putAll((Map<String, Object>) values);
    }

    aggregatedData.put(getIdentifier(), providerData);
  }
}

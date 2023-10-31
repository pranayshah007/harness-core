/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.scorecard.datasources.providers;

import static io.harness.idp.common.Constants.PAGERDUTY_IDENTIFIER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.AUTHORIZATION_HEADER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.PAGERDUTY_SERVICE_ID;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.PAGERDUTY_TARGET_URL;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.backstagebeans.BackstageCatalogEntity;
;
import io.harness.idp.scorecard.datapoints.parser.DataPointParserFactory;
import io.harness.idp.scorecard.datapoints.service.DataPointService;
import io.harness.idp.scorecard.datasourcelocations.locations.DataSourceLocationFactory;
import io.harness.idp.scorecard.datasourcelocations.repositories.DataSourceLocationRepository;
import io.harness.idp.scorecard.datasources.repositories.DataSourceRepository;
import io.harness.idp.scorecard.datasources.utils.ConfigReader;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class PagerDutyProvider extends HttpDataSourceProvider {
  private static final String PAGERDUTY_ANNOTATION = "pagerduty.com/service-id";
  private static final String TARGET_URL_EXPRESSION_KEY = "appConfig.proxy.\"/pagerduty\".target";

  private static final String AUTH_TOKEN_EXPRESSION_KEY = "appConfig.proxy.\"/pagerduty\".headers.Authorization";
  protected PagerDutyProvider(DataPointService dataPointService, DataSourceLocationFactory dataSourceLocationFactory,
      DataSourceLocationRepository dataSourceLocationRepository, DataPointParserFactory dataPointParserFactory,
      ConfigReader configReader, DataSourceRepository dataSourceRepository) {
    super(PAGERDUTY_IDENTIFIER, dataPointService, dataSourceLocationFactory, dataSourceLocationRepository,
        dataPointParserFactory, dataSourceRepository);
    this.configReader = configReader;
  }
  final ConfigReader configReader;

  @Override
  public Map<String, Map<String, Object>> fetchData(String accountIdentifier, BackstageCatalogEntity entity,
      Map<String, Set<String>> dataPointsAndInputValues, String configs)
      throws NoSuchAlgorithmException, KeyManagementException {
    Map<String, String> authHeaders = this.getAuthHeaders(accountIdentifier, configs);
    Map<String, String> replaceableHeaders = new HashMap<>(authHeaders);

    String pagerDutyServiceId = entity.getMetadata().getAnnotations().get(PAGERDUTY_ANNOTATION);
    log.info("Pager Duty Service Id fetched from catalog - {}", pagerDutyServiceId);

    String targetUrl = (String) configReader.getConfigValues(accountIdentifier, configs, TARGET_URL_EXPRESSION_KEY);

    if (targetUrl == null) {
      log.info(
          "PagerDuty Provider  - targetUrl is not present in config hence we can assume PagerDuty Plugin is not enabled");
    }

    log.info("Pager duty target url fetched from configs - {}", targetUrl);

    return processOut(accountIdentifier, PAGERDUTY_IDENTIFIER, entity, replaceableHeaders, Collections.emptyMap(),
        prepareUrlReplaceablePairs(pagerDutyServiceId, targetUrl), dataPointsAndInputValues);
  }

  @Override
  public Map<String, String> getAuthHeaders(String accountIdentifier, String configs) {
    String authToken = (String) configReader.getConfigValues(accountIdentifier, configs, AUTH_TOKEN_EXPRESSION_KEY);

    if (authToken == null) {
      log.info(
          "PagerDuty Provider  - authToken is not present in config hence we can assume PagerDuty Plugin is not enabled");
    }
    HashMap<String, String> returnedMap = new HashMap<>();
    returnedMap.put(AUTHORIZATION_HEADER, authToken);
    return returnedMap;
  }

  private Map<String, String> prepareUrlReplaceablePairs(String pagerDutyServiceId, String targetUrl) {
    Map<String, String> possibleReplaceableUrlPairs = new HashMap<>();

    possibleReplaceableUrlPairs.put(PAGERDUTY_SERVICE_ID, pagerDutyServiceId);
    possibleReplaceableUrlPairs.put(PAGERDUTY_TARGET_URL, targetUrl);

    return possibleReplaceableUrlPairs;
  }
}

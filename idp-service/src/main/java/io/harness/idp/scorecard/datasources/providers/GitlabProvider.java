/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasources.providers;

import static io.harness.idp.common.Constants.GITLAB_IDENTIFIER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.API_BASE_URL;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.AUTHORIZATION_HEADER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.PROJECT_PATH;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.REPOSITORY_BRANCH;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.REPO_SCM;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.backstagebeans.BackstageCatalogEntity;
import io.harness.idp.scorecard.datapoints.parser.DataPointParserFactory;
import io.harness.idp.scorecard.datapoints.service.DataPointService;
import io.harness.idp.scorecard.datasourcelocations.locations.DataSourceLocationFactory;
import io.harness.idp.scorecard.datasourcelocations.repositories.DataSourceLocationRepository;
import io.harness.idp.scorecard.datasources.repositories.DataSourceRepository;
import io.harness.idp.scorecard.datasources.utils.ConfigReader;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.UnsupportedEncodingException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class GitlabProvider extends HttpDataSourceProvider {
  final ConfigReader configReader;
  private static final String SOURCE_LOCATION_ANNOTATION = "backstage.io/source-location";
  private static final String HOST_EXPRESSION_KEY = "appConfig.integrations.gitlab.0.host";
  private static final String TOKEN_EXPRESSION_KEY = "appConfig.integrations.gitlab.0.token";
  protected GitlabProvider(DataPointService dataPointService, DataSourceLocationFactory dataSourceLocationFactory,
      DataSourceLocationRepository dataSourceLocationRepository, DataPointParserFactory dataPointParserFactory,
      ConfigReader configReader, DataSourceRepository dataSourceRepository) {
    super(GITLAB_IDENTIFIER, dataPointService, dataSourceLocationFactory, dataSourceLocationRepository,
        dataPointParserFactory, dataSourceRepository);
    this.configReader = configReader;
  }

  @Override
  public Map<String, Map<String, Object>> fetchData(String accountIdentifier, BackstageCatalogEntity entity,
      Map<String, Set<String>> dataPointsAndInputValues, String configs)
      throws UnsupportedEncodingException, JsonProcessingException, NoSuchAlgorithmException, KeyManagementException {
    Map<String, String> authHeaders = this.getAuthHeaders(accountIdentifier, configs);
    Map<String, String> replaceableHeaders = new HashMap<>(authHeaders);
    String catalogLocation = entity.getMetadata().getAnnotations().get(SOURCE_LOCATION_ANNOTATION);
    Map<String, String> possibleReplaceableRequestBodyPairs = new HashMap<>();
    if (catalogLocation != null) {
      possibleReplaceableRequestBodyPairs = prepareRequestBodyReplaceablePairs(catalogLocation);
    }

    return processOut(accountIdentifier, GITLAB_IDENTIFIER, entity, replaceableHeaders,
        possibleReplaceableRequestBodyPairs, prepareUrlReplaceablePairs(configs, accountIdentifier),
        dataPointsAndInputValues);
  }

  @Override
  protected Map<String, String> getAuthHeaders(String accountIdentifier, String configs) {
    String token = (String) configReader.getConfigValues(accountIdentifier, configs, TOKEN_EXPRESSION_KEY);
    return Map.of(AUTHORIZATION_HEADER, "Bearer " + token);
  }

  private Map<String, String> prepareRequestBodyReplaceablePairs(String catalogLocation) {
    Map<String, String> possibleReplaceableRequestBodyPairs = new HashMap<>();

    String[] catalogLocationParts = catalogLocation.split("/");

    try {
      possibleReplaceableRequestBodyPairs.put(REPO_SCM, catalogLocationParts[2]);
      possibleReplaceableRequestBodyPairs.put(PROJECT_PATH, catalogLocationParts[3] + "/" + catalogLocationParts[4]);
      if (catalogLocationParts.length > 6) {
        possibleReplaceableRequestBodyPairs.put(REPOSITORY_BRANCH, catalogLocationParts[6]);
      }
    } catch (ArrayIndexOutOfBoundsException e) {
      log.error("Error occurred while reading source location annotation ", e);
    }

    return possibleReplaceableRequestBodyPairs;
  }

  private Map<String, String> prepareUrlReplaceablePairs(String configs, String accountIdentifier) {
    String apiBaseUrl = (String) configReader.getConfigValues(accountIdentifier, configs, HOST_EXPRESSION_KEY);
    return Map.of(API_BASE_URL, apiBaseUrl);
  }
}

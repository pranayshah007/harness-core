/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasources.providers;

import static io.harness.idp.common.Constants.JIRA_IDENTIFIER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.*;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.backstagebeans.BackstageCatalogEntity;
import io.harness.idp.scorecard.datapoints.parser.DataPointParserFactory;
import io.harness.idp.scorecard.datapoints.service.DataPointService;
import io.harness.idp.scorecard.datasourcelocations.locations.DataSourceLocationFactory;
import io.harness.idp.scorecard.datasourcelocations.repositories.DataSourceLocationRepository;
import io.harness.idp.scorecard.datasources.utils.ConfigReader;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.UnsupportedEncodingException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class JiraProvider extends DataSourceProvider {
  private static final String JIRA_PROJECT_ANNOTATION = "jira/project-key";
  private static final String JIRA_COMPONENT_ANNOTATION = "jira/component";
  private static final String JIRA_TARGET_URL_EXPRESSION_KEY = "appConfig.proxy.\"/jira/api\".target";
  private static final String AUTH_TOKEN_EXPRESSION_KEY = "appConfig.proxy.\"/jira/api\".headers.Authorization";
  final ConfigReader configReader;
  protected JiraProvider(DataPointService dataPointService, DataSourceLocationFactory dataSourceLocationFactory,
      DataSourceLocationRepository dataSourceLocationRepository, DataPointParserFactory dataPointParserFactory,
      ConfigReader configReader) {
    super(JIRA_IDENTIFIER, dataPointService, dataSourceLocationFactory, dataSourceLocationRepository,
        dataPointParserFactory);
    this.configReader = configReader;
  }

  @Override
  public Map<String, Map<String, Object>> fetchData(String accountIdentifier, BackstageCatalogEntity entity,
      Map<String, Set<String>> dataPointsAndInputValues, String configs)
      throws UnsupportedEncodingException, JsonProcessingException, NoSuchAlgorithmException, KeyManagementException {
    Map<String, String> authHeaders = this.getAuthHeaders(accountIdentifier, configs);
    Map<String, String> replaceableHeaders = new HashMap<>(authHeaders);
    Map<String, String> requestBodyPairs = prepareRequestBodyReplaceablePairs(entity);
    Map<String, String> requestUrlPairs = prepareUrlReplaceablePairs(configs, accountIdentifier);
    return processOut(
        accountIdentifier, entity, dataPointsAndInputValues, replaceableHeaders, requestBodyPairs, requestUrlPairs);
  }

  @Override
  protected Map<String, String> getAuthHeaders(String accountIdentifier, String configs) {
    String authToken = (String) configReader.getConfigValues(accountIdentifier, configs, AUTH_TOKEN_EXPRESSION_KEY);
    if (authToken == null) {
      log.info("Jira Provider - authToken is not present in config hence we can assume Jira Plugin is not enabled");
    }
    assert authToken != null;
    return Map.of(AUTHORIZATION_HEADER, authToken);
  }

  private Map<String, String> prepareUrlReplaceablePairs(String configs, String accountIdentifier) {
    String apiBaseUrl =
        (String) configReader.getConfigValues(accountIdentifier, configs, JIRA_TARGET_URL_EXPRESSION_KEY);
    return Map.of(API_BASE_URL, apiBaseUrl);
  }

  private Map<String, String> prepareRequestBodyReplaceablePairs(BackstageCatalogEntity entity) {
    Map<String, String> requestBodyPairs = new HashMap<>();
    String projectKey = entity.getMetadata().getAnnotations().get(JIRA_PROJECT_ANNOTATION);
    String component = entity.getMetadata().getAnnotations().get(JIRA_COMPONENT_ANNOTATION);
    StringBuilder builder = new StringBuilder();
    builder.append(projectKey);
    if (component != null) {
      builder.append(" AND ").append("component = ").append(component);
    }
    requestBodyPairs.put(PROJECT_COMPONENT_REPLACER, builder.toString());
    return requestBodyPairs;
  }
}

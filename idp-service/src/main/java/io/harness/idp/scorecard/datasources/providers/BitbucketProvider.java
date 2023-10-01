/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasources.providers;

import static io.harness.data.encoding.EncodingUtils.encodeBase64;
import static io.harness.idp.common.Constants.BITBUCKET_IDENTIFIER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.AUTHORIZATION_HEADER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.REPOSITORY_BRANCH;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.REPOSITORY_NAME;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.REPO_SCM;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.WORKSPACE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.backstagebeans.BackstageCatalogEntity;
import io.harness.idp.scorecard.datapoints.parser.DataPointParserFactory;
import io.harness.idp.scorecard.datapoints.service.DataPointService;
import io.harness.idp.scorecard.datasourcelocations.locations.DataSourceLocationFactory;
import io.harness.idp.scorecard.datasourcelocations.repositories.DataSourceLocationRepository;
import io.harness.idp.scorecard.datasources.utils.ConfigReader;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class BitbucketProvider extends DataSourceProvider {
  final ConfigReader configReader;
  private static final String SOURCE_LOCATION_ANNOTATION = "backstage.io/source-location";
  private static final String USERNAME_EXPRESSION_KEY = "appConfig.integrations.bitbucketCloud.0.username";
  private static final String PASSWORD_EXPRESSION_KEY = "appConfig.integrations.bitbucketCloud.0.appPassword";
  protected BitbucketProvider(DataPointService dataPointService, DataSourceLocationFactory dataSourceLocationFactory,
      DataSourceLocationRepository dataSourceLocationRepository, DataPointParserFactory dataPointParserFactory,
      ConfigReader configReader) {
    super(BITBUCKET_IDENTIFIER, dataPointService, dataSourceLocationFactory, dataSourceLocationRepository,
        dataPointParserFactory);
    this.configReader = configReader;
  }

  @Override
  public Map<String, Map<String, Object>> fetchData(String accountIdentifier, BackstageCatalogEntity entity,
      Map<String, Set<String>> dataPointsAndInputValues, String configs)
      throws NoSuchAlgorithmException, KeyManagementException {
    Map<String, String> authHeaders = this.getAuthHeaders(accountIdentifier, configs);
    Map<String, String> replaceableHeaders = new HashMap<>(authHeaders);

    String catalogLocation = entity.getMetadata().getAnnotations().get(SOURCE_LOCATION_ANNOTATION);
    Map<String, String> possibleReplaceableUrlBodyPairs = new HashMap<>();
    if (catalogLocation != null) {
      possibleReplaceableUrlBodyPairs = preparePossibleReplaceableUrlPairs(catalogLocation);
    }

    return processOut(accountIdentifier, entity, dataPointsAndInputValues, replaceableHeaders, Collections.emptyMap(),
        possibleReplaceableUrlBodyPairs);
  }

  @Override
  protected Map<String, String> getAuthHeaders(String accountIdentifier, String configs) {
    String username = (String) configReader.getConfigValues(accountIdentifier, configs, USERNAME_EXPRESSION_KEY);
    String password = (String) configReader.getConfigValues(accountIdentifier, configs, PASSWORD_EXPRESSION_KEY);
    String authToken = encodeBase64(username + ":" + password);
    return Map.of(AUTHORIZATION_HEADER, "Basic " + authToken);
  }

  private Map<String, String> preparePossibleReplaceableUrlPairs(String catalogLocation) {
    Map<String, String> possibleReplaceableUrlBodyPairs = new HashMap<>();

    String[] catalogLocationParts = catalogLocation.split("/");

    try {
      possibleReplaceableUrlBodyPairs.put(REPO_SCM, catalogLocationParts[2]);
      possibleReplaceableUrlBodyPairs.put(WORKSPACE, catalogLocationParts[3]);
      possibleReplaceableUrlBodyPairs.put(REPOSITORY_NAME, catalogLocationParts[4]);
      if (catalogLocationParts.length > 6) {
        possibleReplaceableUrlBodyPairs.put(REPOSITORY_BRANCH, catalogLocationParts[6]);
      }
    } catch (ArrayIndexOutOfBoundsException e) {
      log.error("Error occurred while reading source location annotation ", e);
    }

    return possibleReplaceableUrlBodyPairs;
  }
}

/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasources.providers.scm;

import static io.harness.data.encoding.EncodingUtils.encodeBase64;
import static io.harness.idp.common.Constants.BITBUCKET_IDENTIFIER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.AUTHORIZATION_HEADER;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.backstagebeans.BackstageCatalogEntity;
import io.harness.idp.scorecard.datapoints.parser.factory.DataPointParserFactory;
import io.harness.idp.scorecard.datapoints.service.DataPointService;
import io.harness.idp.scorecard.datasourcelocations.locations.DataSourceLocationFactory;
import io.harness.idp.scorecard.datasourcelocations.repositories.DataSourceLocationRepository;
import io.harness.idp.scorecard.datasources.repositories.DataSourceRepository;
import io.harness.idp.scorecard.datasources.utils.ConfigReader;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@OwnedBy(HarnessTeam.IDP)
public class BitbucketProvider extends ScmBaseProvider {
  private static final String USERNAME_EXPRESSION_KEY = "appConfig.integrations.bitbucketCloud.0.username";
  private static final String PASSWORD_EXPRESSION_KEY = "appConfig.integrations.bitbucketCloud.0.appPassword";

  final ConfigReader configReader;

  public BitbucketProvider(DataPointService dataPointService, DataSourceLocationFactory dataSourceLocationFactory,
      DataSourceLocationRepository dataSourceLocationRepository, DataPointParserFactory dataPointParserFactory,
      ConfigReader configReader, DataSourceRepository dataSourceRepository) {
    super(BITBUCKET_IDENTIFIER, dataPointService, dataSourceLocationFactory, dataSourceLocationRepository,
        dataPointParserFactory, dataSourceRepository);
    this.configReader = configReader;
  }

  @Override
  public Map<String, Map<String, Object>> fetchData(String accountIdentifier, BackstageCatalogEntity entity,
      List<DataFetchDTO> dataPointsAndInputValues, String configs) {
    Map<String, String> possibleReplaceableUrlBodyPairs = new HashMap<>();
    String catalogLocation = entity.getMetadata().getAnnotations().get(SOURCE_LOCATION_ANNOTATION);
    if (catalogLocation != null) {
      possibleReplaceableUrlBodyPairs = prepareUrlReplaceablePairs(catalogLocation);
    }
    return scmProcessOut(accountIdentifier, entity, dataPointsAndInputValues, configs, possibleReplaceableUrlBodyPairs);
  }

  @Override
  protected Map<String, String> prepareUrlReplaceablePairs(String... keysValues) {
    return prepareRequestBodyReplaceablePairs(keysValues[0]);
  }

  @Override
  protected Map<String, String> getAuthHeaders(String accountIdentifier, String configs) {
    String username = (String) configReader.getConfigValues(accountIdentifier, configs, USERNAME_EXPRESSION_KEY);
    String password = (String) configReader.getConfigValues(accountIdentifier, configs, PASSWORD_EXPRESSION_KEY);
    String authToken = encodeBase64(username + ":" + password);
    return Map.of(AUTHORIZATION_HEADER, "Basic " + authToken);
  }
}

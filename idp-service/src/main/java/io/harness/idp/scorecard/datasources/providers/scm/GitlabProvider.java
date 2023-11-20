/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasources.providers.scm;

import static io.harness.idp.common.Constants.GITLAB_IDENTIFIER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.API_BASE_URL;
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

import java.util.List;
import java.util.Map;

@OwnedBy(HarnessTeam.IDP)
public class GitlabProvider extends ScmBaseProvider {
  private static final String HOST_EXPRESSION_KEY = "appConfig.integrations.gitlab.0.host";
  private static final String TOKEN_EXPRESSION_KEY = "appConfig.integrations.gitlab.0.token";

  final ConfigReader configReader;

  public GitlabProvider(DataPointService dataPointService, DataSourceLocationFactory dataSourceLocationFactory,
      DataSourceLocationRepository dataSourceLocationRepository, DataPointParserFactory dataPointParserFactory,
      ConfigReader configReader, DataSourceRepository dataSourceRepository) {
    super(GITLAB_IDENTIFIER, dataPointService, dataSourceLocationFactory, dataSourceLocationRepository,
        dataPointParserFactory, dataSourceRepository);
    this.configReader = configReader;
  }

  @Override
  public Map<String, Map<String, Object>> fetchData(String accountIdentifier, BackstageCatalogEntity entity,
      List<DataFetchDTO> dataPointsAndInputValues, String configs) {
    Map<String, String> possibleReplaceableUrlBodyPairs = prepareUrlReplaceablePairs(
        API_BASE_URL, (String) configReader.getConfigValues(accountIdentifier, configs, HOST_EXPRESSION_KEY));
    return scmProcessOut(accountIdentifier, entity, dataPointsAndInputValues, configs, possibleReplaceableUrlBodyPairs);
  }

  @Override
  protected Map<String, String> getAuthHeaders(String accountIdentifier, String configs) {
    String token = (String) configReader.getConfigValues(accountIdentifier, configs, TOKEN_EXPRESSION_KEY);
    return Map.of(AUTHORIZATION_HEADER, "Bearer " + token);
  }
}

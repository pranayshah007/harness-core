/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasources.providers;

import static io.harness.idp.common.CommonUtils.getHarnessHostForEnv;
import static io.harness.idp.common.Constants.HARNESS_ACCOUNT;
import static io.harness.idp.common.Constants.HARNESS_IDENTIFIER;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.backstagebeans.BackstageCatalogEntity;
import io.harness.idp.proxy.services.IdpAuthInterceptor;
import io.harness.idp.scorecard.datapoints.parser.factory.DataPointParserFactory;
import io.harness.idp.scorecard.datapoints.service.DataPointService;
import io.harness.idp.scorecard.datasourcelocations.locations.DataSourceLocationFactory;
import io.harness.idp.scorecard.datasourcelocations.repositories.DataSourceLocationRepository;
import io.harness.idp.scorecard.datasources.repositories.DataSourceRepository;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@OwnedBy(HarnessTeam.IDP)
public class HarnessProvider extends HttpDataSourceProvider {
  String env;
  final IdpAuthInterceptor idpAuthInterceptor;

  protected HarnessProvider(DataPointService dataPointService, DataSourceLocationFactory dataSourceLocationFactory,
      DataSourceLocationRepository dataSourceLocationRepository, DataPointParserFactory dataPointParserFactory,
      IdpAuthInterceptor idpAuthInterceptor, String env, DataSourceRepository dataSourceRepository) {
    super(HARNESS_IDENTIFIER, dataPointService, dataSourceLocationFactory, dataSourceLocationRepository,
        dataPointParserFactory, dataSourceRepository);
    this.idpAuthInterceptor = idpAuthInterceptor;
    this.env = env;
  }

  @Override
  public Map<String, Map<String, Object>> fetchData(String accountIdentifier, BackstageCatalogEntity entity,
      List<DataFetchDTO> dataPointsAndInputValues, String configs) {
    Map<String, String> replaceableHeaders = new HashMap<>();
    Map<String, String> authHeaders = this.getAuthHeaders(accountIdentifier, null);
    replaceableHeaders.put(HARNESS_ACCOUNT, accountIdentifier);
    replaceableHeaders.putAll(authHeaders);

    return processOut(accountIdentifier, HARNESS_IDENTIFIER, entity, replaceableHeaders, new HashMap<>(),
        prepareUrlReplaceablePairs(), dataPointsAndInputValues);
  }

  @Override
  protected Map<String, String> prepareUrlReplaceablePairs(String... keysValues) {
    String harnessHost = getHarnessHostForEnv(env);
    return Map.of(HOST, harnessHost);
  }

  @Override
  public Map<String, String> getAuthHeaders(String accountIdentifier, String configs) {
    return idpAuthInterceptor.getAuthHeaders();
  }
}

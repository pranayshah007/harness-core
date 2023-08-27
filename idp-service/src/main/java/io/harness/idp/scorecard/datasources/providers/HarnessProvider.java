/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasources.providers;

import static io.harness.idp.scorecard.datasources.constants.Constants.HARNESS_PROVIDER;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.backstagebeans.BackstageCatalogEntity;
import io.harness.idp.proxy.services.IdpAuthInterceptor;
import io.harness.idp.scorecard.datapoints.parser.DataPointParserFactory;
import io.harness.idp.scorecard.datapoints.service.DataPointService;
import io.harness.idp.scorecard.datasourcelocations.locations.DataSourceLocationFactory;
import io.harness.idp.scorecard.datasourcelocations.repositories.DataSourceLocationRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.IDP)
public class HarnessProvider extends DataSourceProvider {
  protected HarnessProvider(DataPointService dataPointService, DataSourceLocationFactory dataSourceLocationFactory,
      DataSourceLocationRepository dataSourceLocationRepository, DataPointParserFactory dataPointParserFactory,
      IdpAuthInterceptor idpAuthInterceptor) {
    super(HARNESS_PROVIDER, dataPointService, dataSourceLocationFactory, dataSourceLocationRepository,
        dataPointParserFactory);
    this.idpAuthInterceptor = idpAuthInterceptor;
  }

  private static final String HARNESS_ACCOUNT = "Harness-Account";

  final IdpAuthInterceptor idpAuthInterceptor;

  @Override
  public Map<String, Map<String, Object>> fetchData(
      String accountIdentifier, BackstageCatalogEntity entity, Map<String, Set<String>> dataPointsAndInputValues) {
    Map<String, String> replaceableHeaders = new HashMap<>();
    Map<String, String> authHeaders = this.getAuthHeaders(accountIdentifier);
    replaceableHeaders.put(HARNESS_ACCOUNT, accountIdentifier);
    replaceableHeaders.putAll(authHeaders);

    return processOut(accountIdentifier, entity, dataPointsAndInputValues, replaceableHeaders, null);
  }

  @Override
  public Map<String, String> getAuthHeaders(String accountIdentifier) {
    return idpAuthInterceptor.getAuthHeaders();
  }
}

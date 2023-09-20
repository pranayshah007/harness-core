/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasources.providers;

import static io.harness.idp.common.Constants.HARNESS_IDENTIFIER;
import static io.harness.idp.common.Constants.LOCAL_ENV;
import static io.harness.idp.common.Constants.LOCAL_HOST;
import static io.harness.idp.common.Constants.PRE_QA_ENV;
import static io.harness.idp.common.Constants.PRE_QA_HOST;
import static io.harness.idp.common.Constants.PROD_HOST;
import static io.harness.idp.common.Constants.QA_ENV;
import static io.harness.idp.common.Constants.QA_HOST;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.backstagebeans.BackstageCatalogEntity;
import io.harness.idp.common.YamlUtils;
import io.harness.idp.proxy.services.IdpAuthInterceptor;
import io.harness.idp.scorecard.datapoints.parser.DataPointParserFactory;
import io.harness.idp.scorecard.datapoints.service.DataPointService;
import io.harness.idp.scorecard.datasourcelocations.locations.DataSourceLocationFactory;
import io.harness.idp.scorecard.datasourcelocations.repositories.DataSourceLocationRepository;
import io.harness.spec.server.idp.v1.model.MergedPluginConfigs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class HarnessProvider extends DataSourceProvider {
  private static String HOST = "{HOST}";
  protected HarnessProvider(DataPointService dataPointService, DataSourceLocationFactory dataSourceLocationFactory,
      DataSourceLocationRepository dataSourceLocationRepository, DataPointParserFactory dataPointParserFactory,
      IdpAuthInterceptor idpAuthInterceptor, String env) {
    super(HARNESS_IDENTIFIER, dataPointService, dataSourceLocationFactory, dataSourceLocationRepository,
        dataPointParserFactory);
    this.idpAuthInterceptor = idpAuthInterceptor;
    this.env = env;
  }

  private static final String HARNESS_ACCOUNT = "Harness-Account";

  final IdpAuthInterceptor idpAuthInterceptor;
  String env;

  @Override
  public Map<String, Map<String, Object>> fetchData(
      String accountIdentifier, BackstageCatalogEntity entity, Map<String, Set<String>> dataPointsAndInputValues) {
    Map<String, String> replaceableHeaders = new HashMap<>();
    Map<String, String> authHeaders = this.getAuthHeaders(accountIdentifier, null);
    replaceableHeaders.put(HARNESS_ACCOUNT, accountIdentifier);
    replaceableHeaders.putAll(authHeaders);
    log.info(
        "Harness provider is called - account -{}, entity - {}, data points and input values - {} replace headers - {} body replaceable - {} url replaceable -{}",
        accountIdentifier, entity, dataPointsAndInputValues, replaceableHeaders, new HashMap<>(),
        prepareUrlReplaceablePairs(env));

    return processOut(accountIdentifier, entity, dataPointsAndInputValues, replaceableHeaders, new HashMap<>(),
        prepareUrlReplaceablePairs(env));
  }

  @Override
  public Map<String, String> getAuthHeaders(String accountIdentifier, MergedPluginConfigs mergedPluginConfigs) {
    return idpAuthInterceptor.getAuthHeaders();
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
}

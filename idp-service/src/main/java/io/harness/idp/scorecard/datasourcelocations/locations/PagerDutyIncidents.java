/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.scorecard.datasourcelocations.locations;

import static io.harness.idp.common.Constants.DSL_RESPONSE;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.AUTHORIZATION_HEADER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.PAGERDUTY_ANNOTATION_MISSING_ERROR;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.PAGERDUTY_PLUGIN_INVALID_TOKEN_ERROR_MESSAGE;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.PAGERDUTY_PLUGIN_INVALID_URL_ERROR_MESSAGE;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.PAGERDUTY_PLUGIN_NOT_ENABLED_ERROR_MESSAGE;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.PAGERDUTY_SERVICE_ID;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.PAGERDUTY_UNABLE_TO_FETCH_DATA_ERROR_MESSAGE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.backstagebeans.BackstageCatalogEntity;
import io.harness.idp.common.GsonUtils;
import io.harness.idp.scorecard.common.beans.DataSourceConfig;
import io.harness.idp.scorecard.common.beans.HttpConfig;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datasourcelocations.beans.ApiRequestDetails;
import io.harness.idp.scorecard.datasourcelocations.client.DslClient;
import io.harness.idp.scorecard.datasourcelocations.client.DslClientFactory;
import io.harness.idp.scorecard.datasourcelocations.entity.DataSourceLocationEntity;
import io.harness.idp.scorecard.datasourcelocations.entity.HttpDataSourceLocationEntity;

import com.google.inject.Inject;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class PagerDutyIncidents implements DataSourceLocation {
  DslClientFactory dslClientFactory;

  private static final String CURRENT_TIME_IN_UTC_KEY = "{CURRENT_TIME_IN_UTC}";

  @Override
  public Map<String, Object> fetchData(String accountIdentifier, BackstageCatalogEntity backstageCatalogEntity,
      DataSourceLocationEntity dataSourceLocationEntity, Map<DataPointEntity, Set<String>> dataPointsAndInputValues,
      Map<String, String> replaceableHeaders, Map<String, String> possibleReplaceableRequestBodyPairs,
      Map<String, String> possibleReplaceableUrlPairs, DataSourceConfig dataSourceConfig)
      throws NoSuchAlgorithmException, KeyManagementException {
    ApiRequestDetails apiRequestDetails =
        ((HttpDataSourceLocationEntity) dataSourceLocationEntity).getApiRequestDetails();

    String apiUrl = apiRequestDetails.getUrl();

    Map<String, Object> inputValueData = new HashMap<>();

    if (replaceableHeaders.get(AUTHORIZATION_HEADER) == null) {
      inputValueData.put(ERROR_MESSAGE_KEY, PAGERDUTY_PLUGIN_NOT_ENABLED_ERROR_MESSAGE);
      return inputValueData;
    }

    matchAndReplaceHeaders(apiRequestDetails.getHeaders(), replaceableHeaders);
    HttpConfig httpConfig = (HttpConfig) dataSourceConfig;
    apiRequestDetails.getHeaders().putAll(httpConfig.getHeaders());

    String serviceId = possibleReplaceableUrlPairs.get(PAGERDUTY_SERVICE_ID);
    if (serviceId == null) {
      inputValueData.put(ERROR_MESSAGE_KEY, PAGERDUTY_ANNOTATION_MISSING_ERROR);
      return inputValueData;
    }

    apiUrl = constructUrl(httpConfig.getTarget(), apiUrl, possibleReplaceableUrlPairs);

    apiUrl = replaceUrlsPlaceholdersIfAny(apiUrl, getDynamicReplaceableURLPlaceHolders());

    apiRequestDetails.setUrl(apiUrl);

    DslClient dslClient = dslClientFactory.getClient(accountIdentifier, null);
    Response response = getResponse(apiRequestDetails, dslClient, accountIdentifier);

    if (response.getStatus() == 200) {
      inputValueData.put(DSL_RESPONSE, GsonUtils.convertJsonStringToObject(response.getEntity().toString(), Map.class));
    } else if (response.getStatus() == 401) {
      inputValueData.put(ERROR_MESSAGE_KEY, PAGERDUTY_PLUGIN_INVALID_TOKEN_ERROR_MESSAGE);
    } else if (response.getStatus() == 500) {
      inputValueData.put(ERROR_MESSAGE_KEY, PAGERDUTY_PLUGIN_INVALID_URL_ERROR_MESSAGE);
    } else {
      inputValueData.put(ERROR_MESSAGE_KEY, PAGERDUTY_UNABLE_TO_FETCH_DATA_ERROR_MESSAGE);
    }

    return inputValueData;
  }

  Map<String, String> getDynamicReplaceableURLPlaceHolders() {
    LocalDateTime currentDateTime = LocalDateTime.now(ZoneOffset.UTC);
    // Create a formatter to format the date in ISO 8601 UTC format
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
    // Format the current date and time in UTC
    String utcDate = currentDateTime.format(formatter);

    Map<String, String> possibleDynamicUrlReplaceable = new HashMap<>();
    possibleDynamicUrlReplaceable.put(CURRENT_TIME_IN_UTC_KEY, utcDate);

    return possibleDynamicUrlReplaceable;
  }

  @Override
  public String replaceInputValuePlaceholdersIfAny(Map<String, String> dataPointIdsAndInputValues, String requestBody) {
    return null;
  }
}
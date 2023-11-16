/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.locations;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.backstagebeans.BackstageCatalogEntity;
import io.harness.idp.scorecard.common.beans.DataSourceConfig;
import io.harness.idp.scorecard.common.beans.HttpConfig;
import io.harness.idp.scorecard.datasourcelocations.beans.ApiRequestDetails;
import io.harness.idp.scorecard.datasourcelocations.client.DslClient;
import io.harness.idp.scorecard.datasourcelocations.entity.DataSourceLocationEntity;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response;

@OwnedBy(HarnessTeam.IDP)
public abstract class DataSourceLocationLoop extends DataSourceLocation {
  public Map<String, Object> fetchData(String accountIdentifier, BackstageCatalogEntity backstageCatalogEntity,
      DataSourceLocationEntity dataSourceLocationEntity, List<DataFetchDTO> dataPointsAndInputValues,
      Map<String, String> replaceableHeaders, Map<String, String> possibleReplaceableRequestBodyPairs,
      Map<String, String> possibleReplaceableUrlPairs, DataSourceConfig dataSourceConfig) {
    ApiRequestDetails apiRequestDetails = fetchApiRequestDetails(dataSourceLocationEntity);
    matchAndReplaceHeaders(apiRequestDetails.getHeaders(), replaceableHeaders);
    HttpConfig httpConfig = (HttpConfig) dataSourceConfig;
    apiRequestDetails.getHeaders().putAll(httpConfig.getHeaders());
    apiRequestDetails.setUrl(
        constructUrl(httpConfig.getTarget(), apiRequestDetails.getUrl(), possibleReplaceableUrlPairs, null, null));
    Map<String, Object> data = new HashMap<>();

    boolean validateResult = false;
    for (DataFetchDTO dataFetchDTO : dataPointsAndInputValues) {
      if (!validate(dataFetchDTO, data, replaceableHeaders, possibleReplaceableRequestBodyPairs)) {
        validateResult = true;
      }
    }

    if (validateResult) {
      return data;
    }

    String requestBody = constructRequestBody(
        apiRequestDetails, possibleReplaceableRequestBodyPairs, dataPointsAndInputValues, backstageCatalogEntity);
    apiRequestDetails.setRequestBody(requestBody);
    DslClient dslClient = dslClientFactory.getClient(accountIdentifier, getHost(possibleReplaceableRequestBodyPairs));
    Response response = getResponse(apiRequestDetails, dslClient, accountIdentifier);
    Map<String, Object> ruleData = processResponse(response);

    for (DataFetchDTO dataFetchDTO : dataPointsAndInputValues) {
      data.put(dataFetchDTO.getRuleIdentifier(), ruleData);
    }

    return data;
  }

  protected String constructRequestBody(ApiRequestDetails apiRequestDetails,
      Map<String, String> possibleReplaceableRequestBodyPairs, List<DataFetchDTO> dataPointsAndInputValues,
      BackstageCatalogEntity backstageCatalogEntity) {
    String requestBody = apiRequestDetails.getRequestBody();
    requestBody =
        replaceInputValuePlaceholdersIfAnyInRequestBody(requestBody, dataPointsAndInputValues, backstageCatalogEntity);
    return replaceRequestBodyPlaceholdersIfAny(possibleReplaceableRequestBodyPairs, requestBody);
  }

  protected abstract String replaceInputValuePlaceholdersIfAnyInRequestBody(
      String requestBody, List<DataFetchDTO> dataPointsAndInputValues, BackstageCatalogEntity backstageCatalogEntity);
}

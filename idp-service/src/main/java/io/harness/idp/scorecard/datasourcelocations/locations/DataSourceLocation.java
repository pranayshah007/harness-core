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
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datasourcelocations.beans.ApiRequestDetails;
import io.harness.idp.scorecard.datasourcelocations.client.DslClient;
import io.harness.idp.scorecard.datasourcelocations.entity.DataSourceLocationEntity;
import io.harness.idp.scorecard.datasourcelocations.entity.HttpDataSourceLocationEntity;
import io.harness.spec.server.idp.v1.model.InputValue;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.apache.commons.math3.util.Pair;

@OwnedBy(HarnessTeam.IDP)
public interface DataSourceLocation {
  Map<String, Object> fetchData(String accountIdentifier, BackstageCatalogEntity backstageCatalogEntity,
      DataSourceLocationEntity dataSourceLocationEntity,
      List<Pair<DataPointEntity, List<InputValue>>> dataPointsAndInputValues, Map<String, String> replaceableHeaders,
      Map<String, String> possibleReplaceableRequestBodyPairs, Map<String, String> possibleReplaceableUrlPairs)
      throws NoSuchAlgorithmException, KeyManagementException;

  String replaceInputValuePlaceholdersIfAny(
      String requestBody, DataPointEntity dataPoint, List<InputValue> inputValues);

  default ApiRequestDetails fetchApiRequestDetails(DataSourceLocationEntity dataSourceLocationEntity) {
    return ((HttpDataSourceLocationEntity) dataSourceLocationEntity).getApiRequestDetails();
  }

  default String constructRequestBody(ApiRequestDetails apiRequestDetails,
      Map<String, String> possibleReplaceableRequestBodyPairs, DataPointEntity dataPoint,
      List<InputValue> inputValues) {
    String requestBody = apiRequestDetails.getRequestBody();
    requestBody = replaceRequestBodyPlaceholdersIfAny(possibleReplaceableRequestBodyPairs, requestBody);
    return replaceInputValuePlaceholdersIfAny(requestBody, dataPoint, inputValues);
  }

  default String constructUrl(
      String url, Map<String, String> replaceableUrls, DataPointEntity dataPoint, List<InputValue> inputValues) {
    String replacedUrl = replaceUrlsPlaceholdersIfAny(url, replaceableUrls);
    return replaceInputValuePlaceholdersIfAny(replacedUrl, dataPoint, inputValues);
  }

  default void matchAndReplaceHeaders(Map<String, String> headers, Map<String, String> replaceableHeaders) {
    headers.forEach((k, v) -> {
      if (replaceableHeaders.containsKey(k)) {
        headers.put(k, replaceableHeaders.get(k));
      }
    });
  }

  default String replaceRequestBodyPlaceholdersIfAny(
      Map<String, String> possibleReplaceableRequestBodyPairs, String requestBody) {
    for (Map.Entry<String, String> entry : possibleReplaceableRequestBodyPairs.entrySet()) {
      requestBody = requestBody.replace(entry.getKey(), entry.getValue());
    }
    return requestBody;
  }

  default Map<String, String> convertDataPointEntityMapToDataPointIdMap(
      Map<DataPointEntity, String> dataPointsAndInputValue) {
    Map<String, String> dataPointIdsAndInputValue = new HashMap<>();
    dataPointsAndInputValue.forEach((k, v) -> dataPointIdsAndInputValue.put(k.getIdentifier(), v));
    return dataPointIdsAndInputValue;
  }

  default String replaceUrlsPlaceholdersIfAny(String url, Map<String, String> replaceableUrls) {
    for (Map.Entry<String, String> entry : replaceableUrls.entrySet()) {
      url = url.replace(entry.getKey(), entry.getValue());
    }
    return url;
  }

  default Response getResponse(ApiRequestDetails apiRequestDetails, DslClient dslClient, String accountIdentifier)
      throws NoSuchAlgorithmException, KeyManagementException {
    return dslClient.call(accountIdentifier, apiRequestDetails);
  }

  default void addInputValueResponse(
      Map<String, Object> data, List<InputValue> inputValues, Map<String, Object> value) {
    for (int i = inputValues.size() - 1; i >= 0; i--) {
      value = Map.of(inputValues.get(i).getValue(), value);
    }
    data.putAll(value);
  }
}

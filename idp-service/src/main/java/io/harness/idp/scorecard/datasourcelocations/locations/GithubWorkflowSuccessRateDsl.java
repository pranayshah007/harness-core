/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.locations;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.Constants.DSL_RESPONSE;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;
import static io.harness.idp.common.Constants.MESSAGE_KEY;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.SOURCE_LOCATION_ANNOTATION_ERROR;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.WORKFLOW_SUCCESS_RATE;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.REPOSITORY_NAME;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.REPOSITORY_OWNER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.REPO_SCM;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ResponseMessage;
import io.harness.idp.backstagebeans.BackstageCatalogEntity;
import io.harness.idp.common.GsonUtils;
import io.harness.idp.scorecard.common.beans.DataSourceConfig;
import io.harness.idp.scorecard.common.beans.HttpConfig;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datasourcelocations.beans.ApiRequestDetails;
import io.harness.idp.scorecard.datasourcelocations.client.DslClient;
import io.harness.idp.scorecard.datasourcelocations.client.DslClientFactory;
import io.harness.idp.scorecard.datasourcelocations.entity.DataSourceLocationEntity;

import com.google.inject.Inject;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class GithubWorkflowSuccessRateDsl implements DataSourceLocation {
  DslClientFactory dslClientFactory;
  private static final String WORKFLOW_ID = "{WORKFLOW_ID}";

  @Override
  public Map<String, Object> fetchData(String accountIdentifier, BackstageCatalogEntity backstageCatalogEntity,
      DataSourceLocationEntity dataSourceLocationEntity, Map<DataPointEntity, Set<String>> dataPointsAndInputValues,
      Map<String, String> replaceableHeaders, Map<String, String> possibleReplaceableRequestBodyPairs,
      Map<String, String> possibleReplaceableUrlPairs, DataSourceConfig dataSourceConfig)
      throws NoSuchAlgorithmException, KeyManagementException {
    ApiRequestDetails apiRequestDetails = fetchApiRequestDetails(dataSourceLocationEntity);
    matchAndReplaceHeaders(apiRequestDetails.getHeaders(), replaceableHeaders);
    HttpConfig httpConfig = (HttpConfig) dataSourceConfig;
    apiRequestDetails.getHeaders().putAll(httpConfig.getHeaders());
    Map<String, Object> data = new HashMap<>();

    Optional<Map.Entry<DataPointEntity, Set<String>>> dataPointAndInputValuesOpt =
        dataPointsAndInputValues.entrySet()
            .stream()
            .filter(entry -> entry.getKey().getIdentifier().equals(WORKFLOW_SUCCESS_RATE))
            .findFirst();

    if (dataPointAndInputValuesOpt.isEmpty()) {
      return data;
    }

    DataPointEntity dataPoint = dataPointAndInputValuesOpt.get().getKey();
    Set<String> inputValues = dataPointAndInputValuesOpt.get().getValue();
    String tempUrl = apiRequestDetails.getUrl(); // using temp variable to store unchanged url

    for (String inputValue : inputValues) {
      if (isEmpty(possibleReplaceableRequestBodyPairs.get(REPO_SCM))
          || isEmpty(possibleReplaceableRequestBodyPairs.get(REPOSITORY_OWNER))
          || isEmpty(possibleReplaceableRequestBodyPairs.get(REPOSITORY_NAME))) {
        data.put(inputValue, Map.of(ERROR_MESSAGE_KEY, SOURCE_LOCATION_ANNOTATION_ERROR));
        continue;
      }
      Map<DataPointEntity, String> dataPointAndInputValueToFetch = Map.of(dataPoint, inputValue);
      Map<String, String> replaceablePairs = new HashMap<>();
      replaceablePairs.putAll(possibleReplaceableUrlPairs);
      replaceablePairs.putAll(possibleReplaceableRequestBodyPairs);
      String url = constructUrl(httpConfig.getTarget(), tempUrl, replaceablePairs, dataPointAndInputValueToFetch);
      apiRequestDetails.setUrl(url);
      DslClient dslClient =
          dslClientFactory.getClient(accountIdentifier, possibleReplaceableRequestBodyPairs.get(REPO_SCM));
      Response response = getResponse(apiRequestDetails, dslClient, accountIdentifier);
      Map<String, Object> inputValueData = new HashMap<>();
      if (response.getStatus() == 200) {
        inputValueData.put(
            DSL_RESPONSE, GsonUtils.convertJsonStringToObject(response.getEntity().toString(), Map.class));
      } else if (response.getStatus() == 500) {
        inputValueData.put(ERROR_MESSAGE_KEY, ((ResponseMessage) response.getEntity()).getMessage());
      } else {
        inputValueData.put(ERROR_MESSAGE_KEY,
            GsonUtils.convertJsonStringToObject(response.getEntity().toString(), Map.class).get(MESSAGE_KEY));
      }
      data.put(inputValue, inputValueData);
    }
    return data;
  }

  @Override
  public String replaceInputValuePlaceholdersIfAny(Map<String, String> dataPointIdsAndInputValue, String url) {
    if (!isEmpty(dataPointIdsAndInputValue.get(WORKFLOW_SUCCESS_RATE))) {
      String inputValue = dataPointIdsAndInputValue.get(WORKFLOW_SUCCESS_RATE);
      inputValue = inputValue.replace("\"", "");
      url = url.replace(WORKFLOW_ID, inputValue);
    }
    return url;
  }
}

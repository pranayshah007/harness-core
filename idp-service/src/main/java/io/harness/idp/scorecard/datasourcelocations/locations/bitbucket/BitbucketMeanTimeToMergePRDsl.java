/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.locations.bitbucket;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.Constants.DEFAULT_BRANCH_KEY_ESCAPED;
import static io.harness.idp.common.Constants.DSL_RESPONSE;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;
import static io.harness.idp.common.Constants.MESSAGE_KEY;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.PULL_REQUEST_MEAN_TIME_TO_MERGE;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.SOURCE_LOCATION_ANNOTATION_ERROR;
import static io.harness.idp.scorecard.datapoints.constants.Inputs.BRANCH_NAME;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.REPOSITORY_NAME;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.REPO_SCM;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.WORKSPACE;

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
import io.harness.idp.scorecard.datasourcelocations.locations.DataSourceLocation;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;
import io.harness.spec.server.idp.v1.model.InputValue;

import com.google.inject.Inject;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.IDP)
public class BitbucketMeanTimeToMergePRDsl implements DataSourceLocation {
  DslClientFactory dslClientFactory;
  private static final String DESTINATION_BRANCH_NAME_REPLACER = "{DESTINATION_BRANCH_NAME_REPLACER}";

  @Override
  public Map<String, Object> fetchData(String accountIdentifier, BackstageCatalogEntity backstageCatalogEntity,
      DataSourceLocationEntity dataSourceLocationEntity, List<DataFetchDTO> dataPointsAndInputValues,
      Map<String, String> replaceableHeaders, Map<String, String> possibleReplaceableRequestBodyPairs,
      Map<String, String> possibleReplaceableUrlPairs, DataSourceConfig dataSourceConfig)
      throws NoSuchAlgorithmException, KeyManagementException {
    ApiRequestDetails apiRequestDetails = fetchApiRequestDetails(dataSourceLocationEntity);
    matchAndReplaceHeaders(apiRequestDetails.getHeaders(), replaceableHeaders);
    HttpConfig httpConfig = (HttpConfig) dataSourceConfig;
    apiRequestDetails.getHeaders().putAll(httpConfig.getHeaders());
    Map<String, Object> data = new HashMap<>();
    String tempUrl = apiRequestDetails.getUrl(); // using temp variable to store unchanged url

    for (DataFetchDTO dataFetchDTO : dataPointsAndInputValues) {
      DataPointEntity dataPoint = dataFetchDTO.getDataPoint();
      List<InputValue> inputValues = dataFetchDTO.getInputValues();

      if (isEmpty(possibleReplaceableUrlPairs.get(REPO_SCM)) || isEmpty(possibleReplaceableUrlPairs.get(WORKSPACE))
          || isEmpty(possibleReplaceableUrlPairs.get(REPOSITORY_NAME))) {
        data.put(dataFetchDTO.getRuleIdentifier(), Map.of(ERROR_MESSAGE_KEY, SOURCE_LOCATION_ANNOTATION_ERROR));
        continue;
      }
      String url = constructUrl(httpConfig.getTarget(), tempUrl, possibleReplaceableUrlPairs, dataPoint, inputValues);
      apiRequestDetails.setUrl(url);
      DslClient dslClient =
          dslClientFactory.getClient(accountIdentifier, possibleReplaceableRequestBodyPairs.get(REPO_SCM));
      Response response = getResponse(apiRequestDetails, dslClient, accountIdentifier);
      Map<String, Object> ruleData = new HashMap<>();
      if (response.getStatus() == 200) {
        ruleData.put(DSL_RESPONSE, GsonUtils.convertJsonStringToObject(response.getEntity().toString(), Map.class));
      } else if (response.getStatus() == 500) {
        ruleData.put(ERROR_MESSAGE_KEY, ((ResponseMessage) response.getEntity()).getMessage());
      } else {
        ruleData.put(ERROR_MESSAGE_KEY,
            GsonUtils.convertJsonStringToObject(response.getEntity().toString(), Map.class).get(MESSAGE_KEY));
      }
      data.put(dataFetchDTO.getRuleIdentifier(), ruleData);
    }

    return data;
  }

  @Override
  public String replaceInputValuePlaceholdersIfAny(
      String url, DataPointEntity dataPoint, List<InputValue> inputValues) {
    if (dataPoint.getIdentifier().equals(PULL_REQUEST_MEAN_TIME_TO_MERGE)) {
      Optional<InputValue> inputValueOpt =
          inputValues.stream().filter(inputValue -> inputValue.getKey().equals(BRANCH_NAME)).findFirst();
      if (inputValueOpt.isPresent()) {
        String inputValue = inputValueOpt.get().getValue();
        if (!inputValue.isEmpty()) {
          if (!inputValue.equals(DEFAULT_BRANCH_KEY_ESCAPED)) {
            url = url.replace(DESTINATION_BRANCH_NAME_REPLACER,
                " AND destination.branch.name="
                    + "\"" + inputValue + "\"");
          } else {
            url = url.replace(DESTINATION_BRANCH_NAME_REPLACER, "");
          }
        }
      }
    }
    return url;
  }
}

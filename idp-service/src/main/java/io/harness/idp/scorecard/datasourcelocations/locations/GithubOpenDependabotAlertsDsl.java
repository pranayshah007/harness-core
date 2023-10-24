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
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.OPEN_DEPENDABOT_ALERTS;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.SOURCE_LOCATION_ANNOTATION_ERROR;
import static io.harness.idp.scorecard.datapoints.constants.Inputs.SEVERITY_TYPE;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.REPOSITORY_NAME;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.REPOSITORY_OWNER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.REPO_SCM;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ResponseMessage;
import io.harness.idp.backstagebeans.BackstageCatalogEntity;
import io.harness.idp.common.GsonUtils;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datasourcelocations.beans.ApiRequestDetails;
import io.harness.idp.scorecard.datasourcelocations.client.DslClient;
import io.harness.idp.scorecard.datasourcelocations.client.DslClientFactory;
import io.harness.idp.scorecard.datasourcelocations.entity.DataSourceLocationEntity;
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
import org.apache.commons.math3.util.Pair;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.IDP)
public class GithubOpenDependabotAlertsDsl implements DataSourceLocation {
  DslClientFactory dslClientFactory;
  private static final String SEVERITY_REPLACER = "{SEVERITY_REPLACER}";

  @Override
  public Map<String, Object> fetchData(String accountIdentifier, BackstageCatalogEntity backstageCatalogEntity,
      DataSourceLocationEntity dataSourceLocationEntity,
      List<Pair<DataPointEntity, List<InputValue>>> dataPointsAndInputValues, Map<String, String> replaceableHeaders,
      Map<String, String> possibleReplaceableRequestBodyPairs, Map<String, String> possibleReplaceableUrlPairs)
      throws NoSuchAlgorithmException, KeyManagementException {
    ApiRequestDetails apiRequestDetails = fetchApiRequestDetails(dataSourceLocationEntity);
    matchAndReplaceHeaders(apiRequestDetails.getHeaders(), replaceableHeaders);
    Map<String, Object> data = new HashMap<>();
    String tempUrl = apiRequestDetails.getUrl(); // using temp variable to store unchanged url

    for (Pair<DataPointEntity, List<InputValue>> dataPointAndInputValues : dataPointsAndInputValues) {
      DataPointEntity dataPoint = dataPointAndInputValues.getFirst();
      List<InputValue> inputValues = dataPointAndInputValues.getSecond();

      if (isEmpty(possibleReplaceableRequestBodyPairs.get(REPO_SCM))
          || isEmpty(possibleReplaceableRequestBodyPairs.get(REPOSITORY_OWNER))
          || isEmpty(possibleReplaceableRequestBodyPairs.get(REPOSITORY_NAME))) {
        addInputValueResponse(data, inputValues, Map.of(ERROR_MESSAGE_KEY, SOURCE_LOCATION_ANNOTATION_ERROR));
        return data;
      }

      Map<String, String> replaceablePairs = new HashMap<>();
      replaceablePairs.putAll(possibleReplaceableUrlPairs);
      replaceablePairs.putAll(possibleReplaceableRequestBodyPairs);
      String url = constructUrl(tempUrl, replaceablePairs, dataPoint, inputValues);
      apiRequestDetails.setUrl(url);
      DslClient dslClient =
          dslClientFactory.getClient(accountIdentifier, possibleReplaceableRequestBodyPairs.get(REPO_SCM));
      Response response = getResponse(apiRequestDetails, dslClient, accountIdentifier);
      Map<String, Object> inputValueData = new HashMap<>();
      if (response.getStatus() == 200) {
        inputValueData.put(
            DSL_RESPONSE, GsonUtils.convertJsonStringToObject(response.getEntity().toString(), List.class));
      } else if (response.getStatus() == 500) {
        inputValueData.put(ERROR_MESSAGE_KEY, ((ResponseMessage) response.getEntity()).getMessage());
      } else {
        inputValueData.put(ERROR_MESSAGE_KEY,
            GsonUtils.convertJsonStringToObject(response.getEntity().toString(), Map.class).get(MESSAGE_KEY));
      }
      addInputValueResponse(data, inputValues, inputValueData);
    }

    return data;
  }

  @Override
  public String replaceInputValuePlaceholdersIfAny(
      String url, DataPointEntity dataPoint, List<InputValue> inputValues) {
    if (dataPoint.getIdentifier().equals(OPEN_DEPENDABOT_ALERTS)) {
      Optional<InputValue> inputValueOpt =
          inputValues.stream().filter(inputValue -> inputValue.getKey().equals(SEVERITY_TYPE)).findFirst();
      if (inputValueOpt.isPresent()) {
        String inputValue = inputValueOpt.get().getValue();
        if (!inputValue.isEmpty()) {
          url = url.replace(SEVERITY_REPLACER, inputValue.toLowerCase());
        }
      }
    }
    return url;
  }
}

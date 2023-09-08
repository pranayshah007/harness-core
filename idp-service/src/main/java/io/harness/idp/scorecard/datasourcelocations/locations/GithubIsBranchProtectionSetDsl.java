/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.locations;

import static io.harness.idp.common.Constants.DSL_RESPONSE;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;
import static io.harness.idp.common.Constants.GITHUB_DEFAULT_BRANCH_KEY_ESCAPED;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.GITHUB_IS_BRANCH_PROTECTED;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.REPO_SCM;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.backstagebeans.BackstageCatalogEntity;
import io.harness.idp.common.GsonUtils;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datasourcelocations.beans.ApiRequestDetails;
import io.harness.idp.scorecard.datasourcelocations.client.DslClient;
import io.harness.idp.scorecard.datasourcelocations.client.DslClientFactory;
import io.harness.idp.scorecard.datasourcelocations.entity.DataSourceLocationEntity;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import org.apache.commons.collections.CollectionUtils;

@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class GithubIsBranchProtectionSetDsl implements DataSourceLocation {
  private static final String BRANCH_PROTECTION_REPLACER = "{BRANCH_PROTECTION_REPLACER}";
  private static final String REPOSITORY_BRANCH_NAME_TEMPLATE =
      "\\n    %s {\\n      name\\n      branchProtectionRule{\\n          allowsDeletions\\n          allowsForcePushes\\n      }\\n    },\\n  ";
  DslClientFactory dslClientFactory;

  @Override
  public Map<String, Object> fetchData(String accountIdentifier, BackstageCatalogEntity backstageCatalogEntity,
      DataSourceLocationEntity dataSourceLocationEntity, Map<DataPointEntity, Set<String>> dataPointsAndInputValues,
      Map<String, String> replaceableHeaders, Map<String, String> possibleReplaceableRequestBodyPairs,
      Map<String, String> possibleReplaceableUrlPairs) {
    ApiRequestDetails apiRequestDetails = fetchApiRequestDetails(dataSourceLocationEntity);
    Map<String, String> headers = apiRequestDetails.getHeaders();
    matchAndReplaceHeaders(headers, replaceableHeaders);
    Map<String, Object> data = new HashMap<>();

    Optional<Map.Entry<DataPointEntity, Set<String>>> dataPointAndInputValuesOpt =
        dataPointsAndInputValues.entrySet()
            .stream()
            .filter(entry -> entry.getKey().getIdentifier().equals(GITHUB_IS_BRANCH_PROTECTED))
            .findFirst();

    if (dataPointAndInputValuesOpt.isEmpty()) {
      return data;
    }
    DataPointEntity dataPoint = dataPointAndInputValuesOpt.get().getKey();
    Set<String> inputValues = dataPointAndInputValuesOpt.get().getValue();

    for (String inputValue : inputValues) {
      Map<DataPointEntity, Set<String>> dataPointsAndInputValuesToFetch = new HashMap<>();
      Set<String> inputValuesToFetch = new HashSet<>();
      inputValuesToFetch.add(inputValue);
      dataPointsAndInputValuesToFetch.put(dataPoint, inputValuesToFetch);
      String requestBody =
          constructRequestBody(apiRequestDetails, possibleReplaceableRequestBodyPairs, dataPointsAndInputValuesToFetch);
      DslClient dslClient =
          dslClientFactory.getClient(accountIdentifier, possibleReplaceableRequestBodyPairs.get(REPO_SCM));

      Response response;
      Map<String, Object> inputValueData = new HashMap<>();
      response = dslClient.call(accountIdentifier, apiRequestDetails.getUrl(), apiRequestDetails.getMethod(),
          apiRequestDetails.getHeaders(), requestBody);
      Map<String, Object> convertedResponse =
          GsonUtils.convertJsonStringToObject(response.getEntity().toString(), Map.class);
      if (response.getStatus() == 200) {
        inputValueData.put(DSL_RESPONSE, convertedResponse);
      } else {
        inputValueData.put(ERROR_MESSAGE_KEY, convertedResponse.get("message"));
      }
      data.put(inputValue, inputValueData);
    }
    return data;
  }

  @Override
  public String replaceRequestBodyInputValuePlaceholdersIfAny(
      Map<String, Set<String>> dataPointsAndInputValues, String requestBody) {
    if (dataPointsAndInputValues.containsKey(GITHUB_IS_BRANCH_PROTECTED)
        && !CollectionUtils.isEmpty(dataPointsAndInputValues.get(GITHUB_IS_BRANCH_PROTECTED))) {
      Set<String> inputValues = dataPointsAndInputValues.get(GITHUB_IS_BRANCH_PROTECTED);
      StringBuilder branchProtectionBuilder = new StringBuilder();
      for (String inputValue : inputValues) {
        if (inputValue != null && !inputValue.equals(GITHUB_DEFAULT_BRANCH_KEY_ESCAPED)) {
          branchProtectionBuilder.append(
              String.format(REPOSITORY_BRANCH_NAME_TEMPLATE, "ref(qualifiedName: \\\"" + inputValue + "\\\")"));
        } else {
          branchProtectionBuilder.append(String.format(REPOSITORY_BRANCH_NAME_TEMPLATE, "defaultBranchRef"));
        }
      }
      requestBody = requestBody.replace(BRANCH_PROTECTION_REPLACER, branchProtectionBuilder.toString());
    }
    return requestBody;
  }
}

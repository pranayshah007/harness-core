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
import io.harness.idp.common.GsonUtils;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datasourcelocations.beans.ApiRequestDetails;
import io.harness.idp.scorecard.datasourcelocations.client.DslClient;
import io.harness.idp.scorecard.datasourcelocations.client.DslClientFactory;
import io.harness.idp.scorecard.datasourcelocations.entity.DataSourceLocationEntity;
import io.harness.idp.scorecard.datasourcelocations.entity.HttpDataSourceLocationEntity;

import com.google.inject.Inject;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Set;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class ProxyThroughDsl implements DataSourceLocation {
  DslClientFactory dslClientFactory;
  @Override
  public Map<String, Object> fetchData(String accountIdentifier, BackstageCatalogEntity backstageCatalogEntity,
      DataSourceLocationEntity dataSourceLocationEntity, Map<DataPointEntity, Set<String>> dataPointsAndInputValues,
      Map<String, String> replaceableHeaders, Map<String, String> possibleReplaceableRequestBodyPairs,
      Map<String, String> possibleReplaceableUrlPairs) throws NoSuchAlgorithmException, KeyManagementException {
    ApiRequestDetails apiRequestDetails =
        ((HttpDataSourceLocationEntity) dataSourceLocationEntity).getApiRequestDetails();
    String apiUrl = apiRequestDetails.getUrl();
    String requestBody = apiRequestDetails.getRequestBody();

    matchAndReplaceHeaders(apiRequestDetails.getHeaders(), replaceableHeaders);
    requestBody = replaceRequestBodyPlaceholdersIfAny(possibleReplaceableRequestBodyPairs, requestBody);
    apiUrl = replaceUrlsPlaceholdersIfAny(apiUrl, possibleReplaceableUrlPairs);

    log.info("ProxyThroughDsl, Replaced API - {} Replaced Body - {} Replaced headers - {}", apiUrl, requestBody,
        apiRequestDetails.getHeaders());

    apiRequestDetails.setRequestBody(requestBody);
    apiRequestDetails.setUrl(apiUrl);
    DslClient dslClient = dslClientFactory.getClient(accountIdentifier, null);
    Response response = getResponse(apiRequestDetails, dslClient, accountIdentifier);

    log.info("Response Status", response.getStatus());
    log.info("Response Entity", response.getEntity().toString());

    return GsonUtils.convertJsonStringToObject(response.getEntity().toString(), Map.class);
  }

  @Override
  public String replaceInputValuePlaceholdersIfAny(Map<String, String> dataPointIdsAndInputValues, String requestBody) {
    return null;
  }
}

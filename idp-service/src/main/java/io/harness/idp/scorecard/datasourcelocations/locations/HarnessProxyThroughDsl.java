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
import io.harness.idp.common.YamlUtils;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datasourcelocations.beans.ApiRequestDetails;
import io.harness.idp.scorecard.datasourcelocations.client.DslClient;
import io.harness.idp.scorecard.datasourcelocations.client.DslClientFactory;
import io.harness.idp.scorecard.datasourcelocations.entity.DataSourceLocationEntity;
import io.harness.idp.scorecard.datasourcelocations.entity.HttpDataSourceLocationEntity;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class HarnessProxyThroughDsl implements DataSourceLocation {
  DslClientFactory dslClientFactory;
  private static String BODY = "{BODY}";
  @Override
  public Map<String, Object> fetchData(String accountIdentifier, BackstageCatalogEntity backstageCatalogEntity,
      DataSourceLocationEntity dataSourceLocationEntity, Map<DataPointEntity, Set<String>> dataPointsAndInputValues,
      Map<String, String> replaceableHeaders, Map<String, String> possibleReplaceableRequestBodyPairs,
      Map<String, String> possibleReplaceableUrlPairs) {
    ApiRequestDetails apiRequestDetails =
        ((HttpDataSourceLocationEntity) dataSourceLocationEntity).getApiRequestDetails();
    String apiUrl = apiRequestDetails.getUrl();
    String requestBody = apiRequestDetails.getRequestBody();

    matchAndReplaceHeaders(apiRequestDetails.getHeaders(), replaceableHeaders);
    log.info("RequestBodyPlaceholder - {}",
        prepareRequestBodyReplaceablePairs(dataPointsAndInputValues, backstageCatalogEntity));
    requestBody = replaceRequestBodyPlaceholdersIfAny(
        prepareRequestBodyReplaceablePairs(dataPointsAndInputValues, backstageCatalogEntity), requestBody);
    apiUrl = replaceUrlsPlaceholdersIfAny(apiUrl, possibleReplaceableUrlPairs);

    log.info("HarnessProxyDsl, Replaced API - {} Replaced Body - {} ", apiUrl, requestBody);

    apiRequestDetails.setRequestBody(requestBody);
    apiRequestDetails.setUrl(apiUrl);
    DslClient dslClient = dslClientFactory.getClient(accountIdentifier, null);
    Response response = getResponse(apiRequestDetails, dslClient, accountIdentifier);

    log.info("Response Status", response.getStatus());
    log.info("Response Entity", response.getEntity().toString());

    return GsonUtils.convertJsonStringToObject(response.getEntity().toString(), Map.class);
  }

  public Map<String, String> prepareRequestBodyReplaceablePairs(
      Map<DataPointEntity, Set<String>> dataPointsAndInputValues, BackstageCatalogEntity backstageCatalogEntity) {
    Map<String, String> possibleReplaceableRequestBodyPairs = new HashMap<>();
    List<JSONObject> dataPointInfoList = new ArrayList<>();
    for (DataPointEntity dataPointEntity : dataPointsAndInputValues.keySet()) {
      JSONObject dataPointInputValues = new JSONObject();
      dataPointInputValues.put(
          "values", dataPointsAndInputValues.get(dataPointEntity).stream().collect(Collectors.toList()));
      dataPointInputValues.put("data_point_identifier", dataPointEntity.getIdentifier());
      dataPointInfoList.add(dataPointInputValues);
    }
    JSONObject dataSourceLocationInfo = new JSONObject();
    dataSourceLocationInfo.put("data_points", dataPointInfoList);

    JSONObject dataSourceDataPointInfo = new JSONObject();
    dataSourceDataPointInfo.put("data_source_location", dataSourceLocationInfo);
    dataSourceDataPointInfo.put("catalog_info_yaml", YamlUtils.writeObjectAsYaml(backstageCatalogEntity));

    JSONObject dataSourceDataPointInfoRequest = new JSONObject();
    dataSourceDataPointInfoRequest.put("request", dataSourceDataPointInfo);

    possibleReplaceableRequestBodyPairs.put(BODY, dataSourceDataPointInfoRequest.toString());
    return possibleReplaceableRequestBodyPairs;
  }

  @Override
  public String replaceRequestBodyInputValuePlaceholdersIfAny(
      Map<String, String> dataPointIdsAndInputValues, String requestBody) {
    return null;
  }
}

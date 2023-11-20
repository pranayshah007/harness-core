/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.locations.harness;

import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.BODY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.backstagebeans.BackstageCatalogEntity;
import io.harness.idp.common.GsonUtils;
import io.harness.idp.common.YamlUtils;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datasourcelocations.locations.DataSourceLocationLoop;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;
import io.harness.spec.server.idp.v1.model.InputValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.json.JSONObject;

@OwnedBy(HarnessTeam.IDP)
public class HarnessProxyThroughDsl extends DataSourceLocationLoop {
  @Override
  protected String replaceInputValuePlaceholdersIfAnyInRequestUrl(
      String url, DataPointEntity dataPoint, List<InputValue> inputValues) {
    return url;
  }

  @Override
  protected boolean validate(DataFetchDTO dataFetchDTO, Map<String, Object> data,
      Map<String, String> replaceableHeaders, Map<String, String> possibleReplaceableRequestBodyPairs) {
    return true;
  }

  @Override
  public String replaceInputValuePlaceholdersIfAnyInRequestBody(
      String requestBody, List<DataFetchDTO> dataPointsAndInputValues, BackstageCatalogEntity backstageCatalogEntity) {
    List<JSONObject> dataPointInfoList = new ArrayList<>();

    for (DataFetchDTO dataFetchDTO : dataPointsAndInputValues) {
      DataPointEntity dataPointEntity = dataFetchDTO.getDataPoint();
      List<InputValue> inputValues = dataFetchDTO.getInputValues();

      JSONObject dataPointInputValues = new JSONObject();
      dataPointInputValues.put("values", inputValues);
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

    return requestBody.replace(BODY, dataSourceDataPointInfoRequest.toString());
  }

  @Override
  protected String getHost(Map<String, String> data) {
    return null;
  }

  @Override
  protected Map<String, Object> processResponse(Response response) {
    return GsonUtils.convertJsonStringToObject(response.getEntity().toString(), Map.class);
  }
}

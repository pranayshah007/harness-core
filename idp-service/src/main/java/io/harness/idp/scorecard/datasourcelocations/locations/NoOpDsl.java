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
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datasourcelocations.entity.DataSourceLocationEntity;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;
import io.harness.spec.server.idp.v1.model.InputValue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response;

@OwnedBy(HarnessTeam.IDP)
public class NoOpDsl extends DataSourceLocationNoLoop {
  static final ObjectMapper mapper =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  @Override
  public Map<String, Object> fetchData(String accountIdentifier, BackstageCatalogEntity backstageCatalogEntity,
      DataSourceLocationEntity dataSourceLocationEntity, List<DataFetchDTO> dataPointAndInputValues,
      Map<String, String> replaceableHeaders, Map<String, String> possibleReplaceableRequestBodyPairs,
      Map<String, String> possibleReplaceableUrlPairs, DataSourceConfig dataSourceConfig) {
    return mapper.convertValue(backstageCatalogEntity, new TypeReference<>() {});
  }

  @Override
  protected String replaceInputValuePlaceholdersIfAnyInRequestUrl(
      String url, DataPointEntity dataPoint, List<InputValue> inputValues) {
    return null;
  }

  @Override
  protected boolean validate(DataFetchDTO dataFetchDTO, Map<String, Object> data,
      Map<String, String> replaceableHeaders, Map<String, String> possibleReplaceableRequestBodyPairs) {
    return true;
  }

  @Override
  public String replaceInputValuePlaceholdersIfAnyInRequestBody(String requestBody, DataPointEntity dataPoint,
      List<InputValue> inputValues, BackstageCatalogEntity backstageCatalogEntity) {
    return null;
  }

  @Override
  protected String getHost(Map<String, String> data) {
    return null;
  }

  @Override
  protected Map<String, Object> processResponse(Response response) {
    return Collections.emptyMap();
  }
}

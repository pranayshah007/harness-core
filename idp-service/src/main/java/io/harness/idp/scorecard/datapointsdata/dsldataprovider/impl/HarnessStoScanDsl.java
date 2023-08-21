/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.scorecard.datapointsdata.dsldataprovider.impl;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.datapoints.parser.DataPointParser;
import io.harness.idp.scorecard.datapoints.parser.DataPointParserFactory;
import io.harness.idp.scorecard.datapointsdata.datapointvalueparser.factory.PipelineInfoResponseFactory;
import io.harness.idp.scorecard.datapointsdata.dsldataprovider.base.DslDataProvider;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.pms.pipeline.PMSPipelineResponseDTO;
import io.harness.remote.client.NGRestUtils;
import io.harness.spec.server.idp.v1.model.DataPointInputValues;
import io.harness.spec.server.idp.v1.model.DataSourceDataPointInfo;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class HarnessStoScanDsl implements DslDataProvider {
  PipelineServiceClient pipelineServiceClient;
  DataPointParserFactory dataPointParserFactory;
  PipelineInfoResponseFactory pipelineInfoFactory;

  @Override
  public Map<String, Object> getDslData(String accountIdentifier, DataSourceDataPointInfo dataSourceDataPointInfo) {
    String ciBaseUrl = dataSourceDataPointInfo.getCiPipelineUrl();
    String[] splitText = ciBaseUrl.split("/");
    String sourceAccountIdentifier = splitText[5];
    String orgIdentifier = splitText[8];
    String projectIdentifier = splitText[10];
    String pipelineIdentifier = splitText[12];

    List<DataPointInputValues> dataPointInputValuesList =
        dataSourceDataPointInfo.getDataSourceLocation().getDataPoints();

    PMSPipelineResponseDTO response = NGRestUtils.getResponse(pipelineServiceClient.getPipelineByIdentifier(
        pipelineIdentifier, sourceAccountIdentifier, orgIdentifier, projectIdentifier, null, null, false));

    Map<String, Object> returnData = new HashMap<>();

    for (DataPointInputValues dataPointInputValues : dataPointInputValuesList) {
      String dataPointIdentifier = dataPointInputValues.getDataPointIdentifier();
      Set<String> inputValues = new HashSet<>(dataPointInputValues.getValues());
      if (!inputValues.isEmpty()) {
        DataPointParser dataPointParser = dataPointParserFactory.getParser(dataPointIdentifier);
        String key = dataPointParser.getReplaceKey();
        log.info("replace key : {}, value: [{}]", key, inputValues);
      }
      returnData.putAll(
          pipelineInfoFactory.getResponseParser(dataPointIdentifier).getParsedValue(response, dataPointIdentifier));
    }
    return returnData;
  }
}

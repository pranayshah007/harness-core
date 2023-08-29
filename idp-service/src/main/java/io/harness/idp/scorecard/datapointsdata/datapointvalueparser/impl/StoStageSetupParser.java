/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.scorecard.datapointsdata.datapointvalueparser.impl;

import io.harness.idp.scorecard.datapointsdata.datapointvalueparser.ValueParserConstants;
import io.harness.idp.scorecard.datapointsdata.datapointvalueparser.ValueParserUtils;
import io.harness.idp.scorecard.datapointsdata.datapointvalueparser.base.PipelineInfo;
import io.harness.pms.pipeline.PMSPipelineResponseDTO;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class StoStageSetupParser implements PipelineInfo {
  private static final String STO_SCAN_STAGE_KEY_IN_YAML = "type: Security";
  public Map<String, Object> getParsedValue(PMSPipelineResponseDTO ciPmsPipelineResponseDTO,
      PMSPipelineResponseDTO cdPmsPipelineResponseDTO, String dataPointIdentifier, String ciPipelineUrl,
      String cdPipelineUrl) {
    ArrayList<String> errorMessagePipelines = new ArrayList<>();

    Map<String, Object> map = new HashMap<>();

    boolean stoCheckForCiPipeline = false;
    if (ciPmsPipelineResponseDTO != null) {
      stoCheckForCiPipeline = ciPmsPipelineResponseDTO.getYamlPipeline().contains(STO_SCAN_STAGE_KEY_IN_YAML);
      if (!stoCheckForCiPipeline) {
        errorMessagePipelines.add(ciPipelineUrl);
      }
    }

    boolean stoCheckForCdPipeline = false;
    if (cdPmsPipelineResponseDTO != null) {
      stoCheckForCdPipeline = cdPmsPipelineResponseDTO.getYamlPipeline().contains(STO_SCAN_STAGE_KEY_IN_YAML);
      if (!stoCheckForCdPipeline) {
        errorMessagePipelines.add(cdPipelineUrl);
      }
    }

    Map<String, Object> dataPointInfo =
        ValueParserUtils.getDataPointsInfoMap(stoCheckForCiPipeline && stoCheckForCdPipeline, errorMessagePipelines);
    map.put(dataPointIdentifier, dataPointInfo);
    return map;
  }
}

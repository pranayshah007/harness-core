/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.scorecard.datapointsdata.datapointvalueparser.impl;

import io.harness.idp.scorecard.datapointsdata.datapointvalueparser.base.PipelineInfo;
import io.harness.pms.pipeline.PMSPipelineResponseDTO;

import java.util.HashMap;
import java.util.Map;

public class StoStageSetupParser implements PipelineInfo {
  private static final String STO_SCAN_STAGE_KEY_IN_YAML = "type: Security";
  public Map<String, Object> getParsedValue(PMSPipelineResponseDTO pmsPipelineResponseDTO, String dataPointIdentifier) {
    Map<String, Object> map = new HashMap<>();
    map.put(dataPointIdentifier, pmsPipelineResponseDTO.getYamlPipeline().contains(STO_SCAN_STAGE_KEY_IN_YAML));
    return map;
  }
}
